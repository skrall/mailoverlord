package org.mailoverlord.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mailoverlord.server.config.MailoverlordSecurityProperties.Mode;

/**
 * The {@code header} mode must never run without both the identity header and a proxy
 * allowlist: trusting a header that nothing vouched for would make the mode a one-line forgery.
 * The record refuses to be constructed, which surfaces as a binding failure at startup.
 */
class TrustedHeaderPropertiesTest {

    @Test
    void noIdentityHeader_isRefused() {
        assertThatThrownBy(() -> properties("", List.of("127.0.0.1/32")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mailoverlord.security.header");
    }

    @Test
    void noTrustedProxy_isRefused() {
        assertThatThrownBy(() -> properties("X-Remote-User", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted-proxies");
    }

    @Test
    void aConfiguredPair_isAccepted() {
        var accepted = new MailoverlordSecurityProperties(Mode.HEADER, List.of(), List.of(),
                "groups", List.of(), "X-Remote-User", "X-Forwarded-Groups",
                List.of("127.0.0.1/32"));
        assertThat(accepted.header()).isEqualTo("X-Remote-User");
        assertThat(accepted.trustedProxies()).containsExactly("127.0.0.1/32");
    }

    private static MailoverlordSecurityProperties properties(String header, List<String> proxies) {
        return new MailoverlordSecurityProperties(Mode.HEADER, List.of(), List.of(), "groups",
                List.of(), header, "X-Forwarded-Groups", proxies);
    }
}