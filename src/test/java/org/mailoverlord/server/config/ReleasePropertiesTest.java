package org.mailoverlord.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The matching rules of the release allowlist, which decide what a release may be sent to.
 *
 * <p>The globs are deliberately small: local and domain halves match separately and in full, so
 * a wildcard in one half cannot bleed into the other and a domain cannot bleed into its
 * subdomains. Everything else is the usual {@code *} behaviour.
 */
class ReleasePropertiesTest {

    @Test
    void noConfigAllowsEverything() {
        assertThat(new ReleaseProperties(null).unrestricted()).isTrue();
        assertThat(new ReleaseProperties(null).allows("anyone@anywhere.test")).isTrue();
        assertThat(new ReleaseProperties(List.of()).unrestricted()).isTrue();
    }

    @Test
    void aWildcardOnTheLocalHalfAllowsAnyNameAtTheLiteralDomain() {
        ReleaseProperties allowlist = new ReleaseProperties(List.of("*@example.com"));

        assertThat(allowlist.allows("boss@example.com")).isTrue();
        assertThat(allowlist.allows("qa+slack@example.com")).as("plus signs are just characters here")
                .isTrue();
        assertThat(allowlist.allows("boss@other.test")).isFalse();
    }

    @Test
    void aPatternWithoutAnAtSignIsAPlainDomainShorthand() {
        ReleaseProperties allowlist = new ReleaseProperties(List.of("example.com"));

        assertThat(allowlist.allows("anyone@example.com")).isTrue();
        assertThat(allowlist.allows("anyone@sub.example.com"))
                .as("the domain half is matched in full, so it does not bleed into subdomains")
                .isFalse();
        assertThat(allowlist.allows("anyone@at-example.com")).isFalse();
    }

    @Test
    void aWildcardInTheDomainHalfStillLeavesItAnchored() {
        ReleaseProperties allowlist = new ReleaseProperties(List.of("qa@*.example.com"));

        assertThat(allowlist.allows("qa@staging.example.com")).isTrue();
        assertThat(allowlist.allows("qa@prod.eu.example.com"))
                .as("* spans dots; the half is a glob, not a label count")
                .isTrue();
        assertThat(allowlist.allows("qa@example.com"))
                .as("one label is still required where the wildcard sits").isFalse();
        assertThat(allowlist.allows("qa@other.test")).isFalse();
    }

    /**
     * The cross-half boundary is what the split exists to enforce: a wildcard on the local half
     * must not be free to consume dots and grow the domain, or {@code *@ourdomain.test} would
     * release mail to {@code x@ourdomain.test.evil}. Within a half, a wildcard still spans dots.
     */
    @Test
    void aLiteralDomainHalfCannotBleedIntoNeighbouringLabels() {
        ReleaseProperties allowlist = new ReleaseProperties(List.of("*@ourdomain.test"));

        assertThat(allowlist.allows("x@sub.ourdomain.test"))
                .as("a local-half wildcard cannot reach into the domain").isFalse();
        assertThat(allowlist.allows("x@evil.example.ourdomain.test"))
                .as("a local-half wildcard cannot append labels to the domain").isFalse();
    }

    @Test
    void matchingIsCaseInsensitive() {
        ReleaseProperties allowlist = new ReleaseProperties(List.of("Boss@Example.COM"));

        assertThat(allowlist.allows("boss@example.com")).isTrue();
        assertThat(allowlist.allows("BOSS@EXAMPLE.COM")).isTrue();
    }

    @Test
    void blankEntriesAreDropped() {
        ReleaseProperties allowlist = new ReleaseProperties(List.of(" ", "*@example.com"));

        assertThat(allowlist.unrestricted()).as("a real entry remains").isFalse();
        assertThat(allowlist.allows("anyone@example.com")).isTrue();
    }

    @Test
    void anAddressWithoutAnAtSignMatchesNothingOnceRestricted() {
        ReleaseProperties allowlist = new ReleaseProperties(List.of("*@example.com"));

        assertThat(allowlist.allows("not-an-address")).isFalse();
    }

    @Test
    void theRefusalNamesTheAddressInAFixedForm() {
        assertThat(ReleaseProperties.refusalMessage("minion@evilland.test"))
                .isEqualTo("Release refused: minion@evilland.test is not an allowed destination.");
    }
}