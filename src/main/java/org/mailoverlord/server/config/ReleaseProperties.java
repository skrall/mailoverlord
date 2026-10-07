package org.mailoverlord.server.config;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the release endpoint.
 *
 * <p>{@code allowedDestinations} narrows where released mail may go. Each entry is a glob: *
 * matches any run of characters, and an entry without an {@code @} is shorthand for any address
 * at that domain. Matching is case-insensitive.
 *
 * <p>Unset means unrestricted. That is the pre-existing behaviour, and it is kept so a release
 * is never silently refused for an operator who never configured the list; the warning is
 * logged at startup instead.
 *
 * @param allowedDestinations the destination patterns released mail may be sent to, or null for
 *                            unrestricted
 */
@ConfigurationProperties("mailoverlord.release")
public record ReleaseProperties(List<String> allowedDestinations) {

    public ReleaseProperties {
        allowedDestinations = allowedDestinations == null ? null
                : allowedDestinations.stream()
                        .filter(pattern -> pattern != null && !pattern.isBlank())
                        .map(pattern -> pattern.trim().toLowerCase(Locale.ROOT))
                        .toList();
    }

    /**
     * Whether the configured list permits this address.
     *
     * <p>When unrestricted, every address is permitted. Otherwise the recipient belongs only if
     * one pattern matches. The candidate is normalised the same way the patterns are, so the
     * caller need not trim or case-fold it first.
     */
    public boolean allows(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        if (unrestricted()) {
            return true;
        }
        String candidate = address.trim().toLowerCase(Locale.ROOT);
        return allowedDestinations.stream().anyMatch(pattern -> matches(pattern, candidate));
    }

    /**
     * Whether no configuration was supplied, so every destination is allowed.
     */
    public boolean unrestricted() {
        return allowedDestinations == null || allowedDestinations.isEmpty();
    }

    /**
     * The reason a refused release is refused, so every rejection names the same format.
     */
    public static String refusalMessage(String address) {
        return "Release refused: " + address + " is not an allowed destination.";
    }

    /**
     * Whether one normalised pattern matches one normalised address.
     *
     * <p>The local and domain halves are matched separately against the same address split, so a
     * wildcard in one half cannot bleed into the other. The domain half is matched in full: a
     * pattern naming {@code example.com} does not match {@code example.com.evil} nor
     * {@code evil.example.com}.
     */
    private static boolean matches(String pattern, String candidate) {
        int patternAt = pattern.indexOf('@');
        String patternLocal = patternAt < 0 ? "*" : pattern.substring(0, patternAt);
        String patternDomain = patternAt < 0 ? pattern : pattern.substring(patternAt + 1);

        int candidateAt = candidate.lastIndexOf('@');
        if (candidateAt < 0) {
            return false;
        }
        String candidateLocal = candidate.substring(0, candidateAt);
        String candidateDomain = candidate.substring(candidateAt + 1);

        return glob(patternLocal).matcher(candidateLocal).matches()
                && glob(patternDomain).matcher(candidateDomain).matches();
    }

    /**
     * Turns one glob into a fully-anchored regex.
     *
     * <p>Every literal segment is {@link Pattern#quote(String) quoted}, so characters that mean
     * something in a regex ({@code .}, {@code +}, {@code @}) are compared literally and only the
     * caller's own * wildcard is special.
     */
    private static Pattern glob(String glob) {
        StringBuilder regex = new StringBuilder();
        String[] segments = glob.split("\\*", -1);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(segments[i]));
        }
        return Pattern.compile(regex.toString());
    }
}