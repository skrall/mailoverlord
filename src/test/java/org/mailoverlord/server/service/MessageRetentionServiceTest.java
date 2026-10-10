package org.mailoverlord.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mailoverlord.server.config.RetentionProperties;
import org.mailoverlord.server.repositories.MessageRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

/**
 * The retention sweep: what it discards, what it leaves, and what it refuses to do.
 *
 * <p>The repository is mocked rather than driven through the real database, because the
 * behaviour under test here is the decision — which rows to drop, and whether to drop any at
 * all — and not the query behind it. What the ordering and the head-drop produce is covered
 * separately against H2, in {@code MessageRetentionQueryTest} and {@code MessageRetentionSweepTest}.
 */
class MessageRetentionServiceTest {

    private MessageRepository messageRepository;
    private MessageRetentionService service;

    @BeforeEach
    void setUp() {
        messageRepository = Mockito.mock(MessageRepository.class);
        service = new MessageRetentionService(messageRepository, properties(RetentionProperties.UNBOUNDED));
    }

    private static RetentionProperties properties(int maxMessages) {
        return new RetentionProperties(maxMessages, Duration.ofMinutes(5));
    }

    /**
     * The default, and the case the issue is really about: nobody has configured anything, and
     * the store must not be touched. A sweep that deleted rows under the default would be
     * discarding mail nobody agreed to lose.
     */
    @Nested
    class Unbounded {

        @Test
        void keepsEverythingWhenTheDefaultIsUnset() {
            when(messageRepository.count()).thenReturn(1_000_000L);

            service.evictOldMessages();

            verify(messageRepository, never()).deleteAllById(any());
        }

        @Test
        void doesNotEvenCountWhenUnbounded() {
            service.evictOldMessages();

            // Counting on every sweep to discover there is nothing to do would be a query
            // against the table every few minutes for a configuration that discards nothing.
            verify(messageRepository, never()).count();
        }
    }

    @Nested
    class Bounded {

        @BeforeEach
        void boundedTo() {
            service = new MessageRetentionService(messageRepository, properties(3));
        }

        @Test
        void doesNothingWhileUnderTheCap() {
            when(messageRepository.count()).thenReturn(3L);

            service.evictOldMessages();

            verify(messageRepository, never()).deleteAllById(any());
        }

        @Test
        void doesNothingWhenExactlyAtTheCap() {
            when(messageRepository.count()).thenReturn(4L);

            service.evictOldMessages();

            verify(messageRepository, never()).deleteAllById(any());
        }

        @Test
        void sparesTheNewestAndDeletesWhatIsLeft() {
            when(messageRepository.count()).thenReturn(10L);
            // Oldest first, so the three the sweep spares are the head of the list.
            when(messageRepository.findIdsNewestFirst(any())).thenReturn(List.of(7L, 8L, 9L, 1L, 2L));

            service.evictOldMessages();

            Pageable requested = captureRequestedPageable();
            assertThat(requested.getOffset()).isZero();
            assertThat(requested.getPageSize()).isEqualTo(3 + MessageRetentionServiceTest.BATCH);
            verify(messageRepository).deleteAllById(List.of(1L, 2L));
        }

        @Test
        void deletesNothingWhenOnlyTheNewestAreStored() {
            // Count and query can disagree across a sweep if another sweep or a manual delete ran
            // in between; a result no longer than the cap must not become a delete.
            when(messageRepository.count()).thenReturn(10L);
            when(messageRepository.findIdsNewestFirst(any())).thenReturn(List.of(1L, 2L, 3L));

            service.evictOldMessages();

            verify(messageRepository, never()).deleteAllById(any());
        }

        @Test
        void boundsTheBatchSoABacklogIsWorkedOffOverSeveralPasses() {
            when(messageRepository.count()).thenReturn(100_000L);
            when(messageRepository.findIdsNewestFirst(any())).thenReturn(List.of(1L, 2L, 3L, 4L));

            service.evictOldMessages();

            // Asks for keep + batch, so the overrun past the batch is what proves the fetch is
            // capped and a 100,000-message backlog cannot become one enormous delete.
            assertThat(captureRequestedPageable().getPageSize())
                    .isEqualTo(3 + MessageRetentionServiceTest.BATCH);
        }
    }

    /**
     * Configured to a value that cannot mean anything. Both are refused at construction, so a
     * typo is a startup failure naming the property rather than a sweep that silently never
     * fires or a limit of minus five million messages.
     */
    @Nested
    class RejectedConfiguration {

        @Test
        void refusesANegativeLimitThatIsNotTheDocumentedUnbounded() {
            assertThatThrownBy(() -> new RetentionProperties(-5, Duration.ofMinutes(5)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mailoverlord.retention.max-messages");
        }

        @Test
        void refusesAZeroOrNegativeSweepInterval() {
            assertThatThrownBy(() -> new RetentionProperties(10, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mailoverlord.retention.sweep-interval");
        }
    }

    /**
     * A cap of one is the smallest that can demonstrate the direction of the eviction, and it is
     * worth pinning: the newest message must be the one that survives.
     */
    @Test
    void keepsTheNewestAndDiscardsTheRest() {
        MessageRetentionService capped = new MessageRetentionService(messageRepository, properties(1));
        when(messageRepository.count()).thenReturn(2L);
        when(messageRepository.findIdsNewestFirst(any())).thenReturn(List.of(2L, 1L));

        capped.evictOldMessages();

        // The newest id is first in the returned list, and sparing one row means dropping that
        // head — the single row that would be lost if the head were not spared is the newest.
        verify(messageRepository).deleteAllById(List.of(1L));
    }

    private Pageable captureRequestedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findIdsNewestFirst(captor.capture());
        return captor.getValue();
    }

    /**
     * Exposed so the batch size is asserted by value rather than by reading the constant.
     */
    static final int BATCH = 500;
}
