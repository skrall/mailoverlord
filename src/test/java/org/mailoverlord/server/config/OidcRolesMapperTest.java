package org.mailoverlord.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

/**
 * The claim plots differ per vendor: Okta ships {@code groups}, Entra ID ships {@code roles},
 * and a one-member group is a plain string rather than a one-element array. The mapper has to
 * normalise all of them, and it has to leave a user whose groups are empty or not configured
 * safely readable, because reading an inbox must never depend on what the IdP happened to put in
 * a claim.
 */
class OidcRolesMapperTest {

    private static final OidcRolesMapper OKTA =
            new OidcRolesMapper("groups", List.of("mailoverlord-operators"));
    private static final OidcRolesMapper ENTRA =
            new OidcRolesMapper("roles", List.of("mailoverlord-operators"));

    private static Set<String> authoritiesOf(OidcRolesMapper mapper, Map<String, Object> claims) {
        return mapper.authoritiesFrom(claims).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Test
    void oktaGroupsArrayMapsToOperator() {
        assertThat(authoritiesOf(OKTA, Map.of("groups", List.of("everyone", "mailoverlord-operators"))))
                .containsExactlyInAnyOrder("ROLE_VIEWER", "ROLE_OPERATOR");
    }

    @Test
    void entraRolesArrayMapsToOperator() {
        assertThat(authoritiesOf(ENTRA, Map.of("roles", List.of("mailoverlord-operators"))))
                .containsExactlyInAnyOrder("ROLE_VIEWER", "ROLE_OPERATOR");
    }

    @Test
    void aSingleGroupArrivingAsAStringMapsToOperator() {
        assertThat(authoritiesOf(OKTA, Map.of("groups", "mailoverlord-operators")))
                .containsExactlyInAnyOrder("ROLE_VIEWER", "ROLE_OPERATOR");
    }

    @Test
    void aGroupThatIsNotConfiguredStaysAReader() {
        assertThat(authoritiesOf(OKTA, Map.of("groups", List.of("everyone"))))
                .containsExactly("ROLE_VIEWER");
    }

    @Test
    void anAbsentClaimStaysAReader() {
        assertThat(authoritiesOf(OKTA, Map.of("email", "someone@example.com")))
                .containsExactly("ROLE_VIEWER");
    }

    @Test
    void groupNamesMatchCaseInsensitively() {
        assertThat(authoritiesOf(OKTA, Map.of("groups", List.of("MAILOVERLORD-OPERATORS"))))
                .containsExactlyInAnyOrder("ROLE_VIEWER", "ROLE_OPERATOR");
    }

    @Test
    void claimsThatAreNeitherStringNorListAreIgnored() {
        assertThat(authoritiesOf(OKTA, Map.of("groups", 42)))
                .containsExactly("ROLE_VIEWER");
    }
}