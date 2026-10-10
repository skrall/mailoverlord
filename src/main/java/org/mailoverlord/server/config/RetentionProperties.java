package org.mailoverlord.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How much captured mail Mailoverlord keeps.
 *
 * <p>{@code maxMessages} is a cap on the number of stored messages rather than a switch. A
 * value of zero or less means keep everything, and that is the default: this is a mail debugger,
 * and silently discarding mail would be a worse surprise than the memory pressure a cap avoids.
 * A long-lived instance in front of a busy test environment can set one; nobody else has to
 * think about it.
 *
 * <p>The cap is a count and not a byte budget. A count is predictable — you know how many
 * messages you will find — where a byte budget would have to sum a BLOB on every sweep, and
 * {@code mailoverlord.smtp.max-message-size} already bounds any single message, so the count is
 * multiplied by a known worst case.
 *
 * @param maxMessages the newest N messages to keep; zero or less keeps every message
 * @param sweepInterval how often the oldest overflow is discarded
 */
@ConfigurationProperties("mailoverlord.retention")
public record RetentionProperties(
        @DefaultValue("-1") int maxMessages,
        @DefaultValue("5m") java.time.Duration sweepInterval) {

    public RetentionProperties {
        if (maxMessages < 0 && maxMessages != UNBOUNDED) {
            throw new IllegalArgumentException(
                    "mailoverlord.retention.max-messages must be a positive count, or exactly -1 "
                            + "to keep every message; it is not a byte budget or a flag");
        }
        if (sweepInterval == null || sweepInterval.isNegative() || sweepInterval.isZero()) {
            throw new IllegalArgumentException(
                    "mailoverlord.retention.sweep-interval must be a positive duration such as 5m");
        }
    }

    /**
     * The value that means keep everything, and the only negative value accepted.
     *
     * <p>Naming it rather than testing {@code < 0} keeps the validation above honest: a typo of
     * {@code -5} is a misconfiguration and is refused at startup, while {@code -1} is the
     * documented way of saying "no cap".
     */
    public static final int UNBOUNDED = -1;

    /**
     * Whether the cap applies at all. False by default, which is why the sweep can be scheduled
     * unconditionally and simply do nothing: one code path, and the tests do not have to swap
     * beans to turn eviction off.
     */
    public boolean isUnbounded() {
        return maxMessages <= 0;
    }
}
