package org.mailoverlord.server.model;

/**
 * What happened to one message in a release batch.
 *
 * <p>Release delivers to real addresses one message at a time, so a batch can partly succeed:
 * once a message has been handed to the SMTP server it cannot be unsent. Reporting per id is what
 * lets an operator retry only the remainder, instead of re-sending what already went out.
 *
 * @param id the message this outcome is about
 * @param released whether the message was handed to the SMTP server
 * @param errorMessage why it was not, or {@code null} when it was
 */
public record MessageReleaseOutcome(Long id, boolean released, String errorMessage) {
}