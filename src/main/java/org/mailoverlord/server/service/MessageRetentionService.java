package org.mailoverlord.server.service;

import java.util.List;

import org.mailoverlord.server.config.RetentionProperties;
import org.mailoverlord.server.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Discards the oldest captured mail once the store passes its retention cap.
 *
 * <p>Without this, nothing bounds how much mail Mailoverlord holds: the only deletion path in
 * the codebase was a manual {@code POST /messages/delete}. The default store is
 * {@code jdbc:h2:mem:mailoverlord}, so every captured byte stays heap-resident for the life of
 * the process, and {@code DATA} is a BLOB with no spill to disk to soften it. On an instance
 * that sits in a test environment receiving mail all day, that is the most likely way it dies —
 * {@link OutOfMemoryError} — rather than anything the application does wrong.
 *
 * <p>Eviction is newest-first with the head spared, so what falls off the end is the oldest.
 * See {@link RetentionProperties} for why that is the shape of the knob, and
 * {@link MessageRepository#findIdsNewestFirst} for why the ordering carries an id tiebreaker.
 */
@Service
public class MessageRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(MessageRetentionService.class);

    /**
     * How many messages one sweep deletes.
     *
     * <p>A cap on the cap, so an instance that has fallen a long way behind (a limit configured
     * after the fact, or a burst arriving between two sweeps) discards its backlog over several
     * passes rather than in one enormous transaction. It also keeps each delete a bounded
     * statement instead of an unbounded IN list.
     */
    private static final int DELETES_PER_SWEEP = 500;

    private final MessageRepository messageRepository;
    private final RetentionProperties properties;

    public MessageRetentionService(MessageRepository messageRepository,
            RetentionProperties properties) {
        this.messageRepository = messageRepository;
        this.properties = properties;
    }

    /**
     * Runs on a fixed delay rather than a cron.
     *
     * <p>There is nothing here to schedule at a particular time of day — retention has no clock
     * of its own, it just keeps the store near its configured size — and a fixed delay measures
     * from the end of the previous sweep, so a slow sweep cannot queue up behind itself.
     *
     * <p>{@code initialDelay} matters as much as the delay: without it the sweep would run
     * during startup, when the schema is still being built, and a test that captures a handful
     * of messages would race the database's own initialization.
     */
    @Scheduled(fixedDelayString = "${mailoverlord.retention.sweep-interval:5m}",
            initialDelayString = "${mailoverlord.retention.sweep-interval:5m}")
    @Transactional
    public void evictOldMessages() {
        if (properties.isUnbounded()) {
            return;
        }
        int keep = properties.maxMessages();
        long stored = messageRepository.count();
        if (stored <= keep) {
            return;
        }

        // One query answers "is there anything to do and which rows" rather than counting and
        // then re-reading. It returns newest first, so the head is what a cap spares and the
        // tail is what falls off the end. The newest `keep` come back first and are dropped here
        // rather than with a page offset: PageRequest has no offset of its own, its `page` and
        // `size` multiply, so PageRequest.of(keep, batch) asks to skip keep*batch rows and
        // silently evicts the newest mail the moment the cap exceeds one. Asking for keep + batch
        // and slicing the head makes that arithmetic impossible to get wrong, and the rows are
        // ids either way, so the extra page costs nothing.
        List<Long> newestFirst = messageRepository.findIdsNewestFirst(
                PageRequest.of(0, keep + DELETES_PER_SWEEP));
        if (newestFirst.size() <= keep) {
            return;
        }
        List<Long> overflow = newestFirst.subList(keep, newestFirst.size());

        messageRepository.deleteAllById(overflow);

        // Logging rather than surfacing it: the message fell off the end of a bounded store, which
        // is what was asked for, but silently is how a QA engineer ends up chasing an email that
        // was never sent when it was evicted. The count is stated so the gap is explainable.
        logger.warn("Retention cap of {} messages exceeded; discarded the {} oldest, leaving {} of {}.",
                keep, overflow.size(), stored - overflow.size(), stored);
    }
}
