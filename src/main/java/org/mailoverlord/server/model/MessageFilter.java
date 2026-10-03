package org.mailoverlord.server.model;

import java.time.Instant;

/**
 * The criteria for filtering a page of the message table.
 *
 * <p>Every field is optional, and a null field means "do not filter on this". The three text
 * fields match on a substring; the two timestamps bound the date the mail arrived.
 *
 * <p>A blank field is treated as absent rather than as a search for the empty string. That
 * distinction is load-bearing rather than cosmetic: the columns are nullable, so a LIKE on an
 * empty pattern would match every row that <em>has</em> a subject and exclude every row that
 * does not. Clearing the search box would then appear to delete the no-subject messages from the
 * table, which is the opposite of what clearing a filter should do.
 *
 * <p>The text fields are matched with LIKE, so their wildcards are escaped on the way in.
 * Without that, a search for {@code 50%} silently also matches {@code 50x}, {@code a_b} matches
 * {@code axb}, and a search for {@code %} returns the entire table, which reads as though the
 * filter had been ignored. The escape character is {@code !} rather than a backslash because a
 * backslash has to survive both a Java literal and a JPQL literal, and getting that wrong is
 * easier than avoiding it.
 */
public record MessageFilter(
        String subject,
        String from,
        String to,
        Instant receivedFrom,
        Instant receivedTo) {

    /** The character that neutralises a LIKE wildcard. Paired with {@code escape '!'} in the query. */
    private static final char LIKE_ESCAPE = '!';

    public MessageFilter {
        subject = escapeForLike(blankToNull(subject));
        from = escapeForLike(blankToNull(from));
        to = escapeForLike(blankToNull(to));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Doubles the escape character and prefixes every LIKE wildcard with it, so that the value
     * the user typed is matched literally. Order matters: the escape character has to be
     * doubled before any wildcard is prefixed with it, or the prefix would itself be escaped.
     */
    private static String escapeForLike(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}