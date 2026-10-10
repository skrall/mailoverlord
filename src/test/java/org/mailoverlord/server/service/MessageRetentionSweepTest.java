package org.mailoverlord.server.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mailoverlord.server.AbstractMailoverlordIntegrationTest;
import org.mailoverlord.server.config.RetentionProperties;
import org.mailoverlord.server.entities.Message;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sweep against a real database, with the repository mocked in the unit test.
 *
 * <p>{@code MessageRetentionServiceTest} proves the sweep asks for the right page and deletes
 * what it is given; {@code MessageRetentionQueryTest} proves the query returns what the sweep
 * asked for. Neither proves the two compose, which is what this does: five stored messages, a
 * cap of three, one sweep, and the three newest are left.
 *
 * <p>The application context is the shared one, so the cap comes from a property rather than a
 * constructed bean. It has to stay {@code -1} — an unbounded store under a scheduled sweep is
 * what every other test expects — which is why the cap is supplied directly to a service built
 * here instead of through {@code @TestPropertySource}.
 */
@Transactional
class MessageRetentionSweepTest extends AbstractMailoverlordIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");


    private MessageRetentionService service;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        // The context is shared and unbounded so no other test has its mail swept away; the cap
        // under test is applied to a service built here, leaving the scheduled bean alone.
        service = new MessageRetentionService(messageRepository,
                new RetentionProperties(3, Duration.ofMinutes(5)));
    }

    private Message store(String subject, int minute) {
        Message message = new Message();
        message.setFrom("from@example.com");
        message.appendTo("to@example.com");
        message.setSubject(subject);
        message.setData(("body of " + subject).getBytes());
        Message saved = messageRepository.save(message);
        saved.setReceivedTimestamp(BASE.plusSeconds(minute * 60L));
        return messageRepository.save(saved);
    }

    /**
     * The subjects still stored, oldest first.
     *
     * <p>Read back through the retention query, which returns newest first, and reversed — so
     * this reads in the order the table shows and the expectations below can be written oldest to
     * newest like the fixtures are. Going through the same query the sweep uses also keeps this
     * class from adding a second way of asking the same question.
     */
    private List<String> storedSubjectsOldestFirst() {
        List<Long> newestFirst = messageRepository.findIdsNewestFirst(PageRequest.of(0, 50));
        List<String> reversed = new java.util.ArrayList<>(newestFirst.size());
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            messageRepository.findById(newestFirst.get(i)).ifPresent(m -> reversed.add(m.getSubject()));
        }
        return reversed;
    }

    @Test
    void keepsTheNewestAndDiscardsTheOldest() {
        store("oldest", 0);
        store("middle", 1);
        store("newest", 2);
        store("newer", 3);
        store("newest-but-one", 4);

        service.evictOldMessages();

        // Five stored, a cap of three, so the two oldest fall off and three remain. The oldest
        // two going rather than the newest two is the whole point: getting that backwards would
        // discard the mail someone is most likely to have just sent and be looking for.
        assertThat(storedSubjectsOldestFirst())
                .containsExactly("newest", "newer", "newest-but-one");
    }

    @Test
    void leavesAStoreAtOrUnderTheCapAlone() {
        store("first", 0);
        store("second", 1);
        store("third", 2);

        service.evictOldMessages();

        assertThat(messageRepository.count()).isEqualTo(3);
    }

    /**
     * A second sweep with nothing new must not delete anything further. Without this, an off-by-one
     * in the offset would show up as a store that shrinks on every pass — a cap of three quietly
     * behaving as a countdown.
     */
    @Test
    void repeatedSweepsDoNotShrinkFurther() {
        store("first", 0);
        store("second", 1);
        store("third", 2);
        store("fourth", 3);
        store("fifth", 4);

        service.evictOldMessages();
        long afterFirst = messageRepository.count();
        service.evictOldMessages();
        service.evictOldMessages();

        assertThat(messageRepository.count()).isEqualTo(afterFirst).isEqualTo(3);
    }

    /**
     * The default keeps everything, so a sweep on an untouched context's own configuration is a
     * no-op however much is stored.
     */
    @Test
    void theUnboundedDefaultDiscardsNothing() {
        MessageRetentionService unbounded = new MessageRetentionService(messageRepository,
                new RetentionProperties(RetentionProperties.UNBOUNDED, Duration.ofMinutes(5)));
        for (int i = 0; i < 8; i++) {
            store("message " + i, i);
        }

        unbounded.evictOldMessages();

        assertThat(messageRepository.count()).isEqualTo(8);
    }
}
