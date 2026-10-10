package org.mailoverlord.server.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mailoverlord.server.AbstractMailoverlordIntegrationTest;
import org.mailoverlord.server.entities.Message;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The query the sweep relies on, against a real database.
 *
 * <p>{@code MessageRetentionServiceTest} mocks the repository and so can only assert which page
 * was requested. What that page actually returns — newest first, so that sparing a cap is a
 * slice of the head — is a property of the JPQL and of the database executing it, and a mocked
 * repository would happily agree with a completely wrong query. These are the tests that would
 * fail if the ordering reversed, which would silently swap the tool from keeping the newest mail
 * to keeping the oldest.
 *
 * <p>{@code @Transactional} on the class rolls each test back, so these rows never reach the
 * shared context that every other test uses.
 */
@Transactional
class MessageRetentionQueryTest extends AbstractMailoverlordIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");


    @BeforeEach
    void clear() {
        messageRepository.deleteAll();
    }

    /**
     * Stores a message with an explicit arrival time, since arrival is what the query orders by
     * and {@code @PrePersist} would otherwise stamp all of them "now".
     */
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

    private List<Long> ids() {
        return messageRepository.findIdsNewestFirst(PageRequest.of(0, 10));
    }

    /**
     * Newest first, so sparing the newest N is dropping the head of this list. If the order ever
     * reversed, the sweep would begin by deleting the most recent mail — the one someone is most
     * likely to be looking at — and this is the assertion that stops it.
     */
    @Test
    void ordersNewestFirstSoSparingACapDropsTheRightRows() {
        Message first = store("first", 0);
        Message second = store("second", 1);
        Message third = store("third", 2);

        // Read once: a second query re-flushes state the first already caused, so two reads in
        // one test can disagree for reasons that have nothing to do with the ordering.
        List<Long> ids = ids();

        assertThat(ids).containsExactly(third.getId(), second.getId(), first.getId());
        // What a cap of one spares is ids.subList(0, 1) — the newest.
        assertThat(ids.subList(0, 1)).containsExactly(third.getId());
    }

    /**
     * Messages captured in the same millisecond must keep a stable order, or two runs over the
     * same data could evict different rows. {@code receivedTimestamp} alone does not guarantee
     * that; the id tiebreaker does, and this asserts the ids come back in descending order so the
     * same rows land past the head on every run.
     */
    @Test
    void breaksTiesByIdSoTheOrderIsStable() {
        Message a = store("tied", 0);
        Message b = store("tied", 0);
        Message c = store("tied", 0);

        List<Long> byIdDescending = Stream.of(a.getId(), b.getId(), c.getId())
                .sorted(Comparator.reverseOrder())
                .toList();

        assertThat(ids()).isEqualTo(byIdDescending);
    }

    /**
     * The cap is on the count, so the sweep's {@code stored <= keep} short-circuit and the
     * head-drop have to agree about how many rows a cap of N leaves behind.
     */
    @Test
    void countAgreesWithWhatTheQueryReturns() {
        store("first", 0);
        store("second", 1);

        assertThat(messageRepository.count()).isEqualTo(2);
        assertThat(ids()).hasSize(2);
    }

    /**
     * The query selects ids rather than entities, so a page of them carries no body. The rows
     * being discarded are exactly the ones nobody will look at, and hydrating each of their
     * BLOBs to throw them away would defeat the point of bounding memory.
     */
    @Test
    void returnsIdsRatherThanMessages() {
        store("first", 0);

        assertThat(ids()).allSatisfy(id -> assertThat(id).isNotNull());
    }
}