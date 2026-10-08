package org.mailoverlord.server.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Turns the group-membership claim an identity provider hands over into the mailoverlord roles.
 *
 * <p>The vendors differ in which claim carries the membership: Okta defaults to {@code groups},
 * Entra ID to {@code roles} (or {@code groups}, which needs admin consent and caps at 150). The
 * claim is configurable via {@code mailoverlord.security.roles-claim} and the operator group
 * names via {@code mailoverlord.security.operator-groups}, so the vendor difference lives in
 * configuration rather than code.
 *
 * <p>The claim shape is not always a JSON array: a single value arrives as a plain string, and
 * Entra carries {@code roles} as well as {@code wids}. Both shapes are normalised, and a group
 * is matched case-insensitively. Everyone authenticated is at least a VIEWER (reading an inbox
 * presupposes being admitted), and an OPERATOR also holds VIEWER, matching the Basic identity
 * model.
 */
public final class OidcRolesMapper {

    private final String rolesClaim;
    private final Set<String> operatorGroups;

    public OidcRolesMapper(String rolesClaim, List<String> operatorGroups) {
        this.rolesClaim = rolesClaim;
        Set<String> groups = new LinkedHashSet<>();
        for (String group : operatorGroups) {
            String trimmed = group.trim();
            if (!trimmed.isEmpty()) {
                groups.add(trimmed.toLowerCase());
            }
        }
        this.operatorGroups = Set.copyOf(groups);
    }

    public Set<GrantedAuthority> authoritiesFrom(Map<String, Object> claims) {
        boolean operator = operatorGroups.isEmpty() ? false : readGroups(claims).stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(operatorGroups::contains);
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_VIEWER"));
        if (operator) {
            authorities.add(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        }
        return authorities;
    }

    private Set<String> readGroups(Map<String, Object> claims) {
        Set<String> groups = new LinkedHashSet<>();
        Object value = claims.get(rolesClaim);
        if (value instanceof String single) {
            groups.add(single);
        } else if (value instanceof String[] array) {
            for (String entry : array) {
                groups.add(entry);
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry != null) {
                    groups.add(entry.toString());
                }
            }
        }
        return groups;
    }
}