package org.mailoverlord.server.model;

import java.time.Instant;

/**
 * A row of the message table.
 *
 * Deliberately omits the message body: the table lists many messages at once and every
 * captured message carries its full MIME content, which can be up to 10 MB. Use
 * {@link MessageDetail} to read a single message in full.
 */
public record MessageSummary(
        Long id,
        String from,
        String to,
        Instant receivedTimestamp,
        Instant releasedTimestamp,
        long sizeBytes,
        String subject) {
}
