package org.mailoverlord.server.model;

import java.time.Instant;

/**
 * A single captured message, including the text of its body.
 */
public record MessageDetail(
        Long id,
        String from,
        String to,
        Instant receivedTimestamp,
        String subject,
        String body) {
}
