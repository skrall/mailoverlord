package org.mailoverlord.server.service;

import jakarta.mail.MessagingException;

/**
 * A recipient the release allowlist refuses.
 *
 * <p>Thrown per message, once the stored message's own recipients are known, so the request can
 * carry on to the next id instead of failing the batch. It is its own type so the service can
 * log the refusal as the expected outcome it is rather than as an error with a stack trace.
 */
public class RecipientNotAllowedException extends MessagingException {

    RecipientNotAllowedException(String message) {
        super(message);
    }
}