package org.mailoverlord.server.model;

import java.time.Instant;
import java.util.List;

/**
 * A single captured message, including the text of its body.
 *
 * <p>{@code parts} describes the whole MIME tree rather than the attachments alone, so a reader
 * can tell a message that arrived complete from one that did not. See {@link MessagePart}.
 *
 * @param parts every leaf part of the message, in the order the MIME tree presents them
 */
public record MessageDetail(
        Long id,
        String from,
        String to,
        Instant receivedTimestamp,
        Instant releasedTimestamp,
        String subject,
        String body,
        List<MessagePart> parts) {
}
