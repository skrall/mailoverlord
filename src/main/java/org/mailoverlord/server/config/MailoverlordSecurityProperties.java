package org.mailoverlord.server.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for request authentication.
 *
 * <p>{@code mode} picks which filter chain guards the API: {@code none} keeps the pre-security
 * behaviour and is what the tests run under, {@code basic} requires HTTP Basic, {@code oidc}
 * signs browsers in against an identity provider through the authorization code flow. The
 * mechanisms compose around this select rather than replacing it. See #56, #57.
 *
 * <p>{@code operatorUsers} and {@code viewerUsers} name identities by user name, sharing one
 * {@code spring.security.user.password}. OPERATOR may release and delete; VIEWER may only read.
 * The {@code spring.security.user.*} identity is always an OPERATOR. Under {@code oidc} the two
 * lists are unused: {@code operatorGroups} and {@code rolesClaim} say which identity-provider
 * groups get the OPERATOR role.
 *
 * @param mode          which authentication mechanism to enforce
 * @param operatorUsers the user names that may release and delete
 * @param viewerUsers   the user names that may only read
 * @param rolesClaim    the OIDC claim that carries group membership
 * @param operatorGroups OIDC group names that may release and delete
 */
@ConfigurationProperties("mailoverlord.security")
public record MailoverlordSecurityProperties(
        @DefaultValue("basic") Mode mode,
        List<String> operatorUsers,
        List<String> viewerUsers,
        @DefaultValue("groups") String rolesClaim,
        List<String> operatorGroups) {

    public MailoverlordSecurityProperties {
        operatorUsers = operatorUsers == null ? List.of() : List.copyOf(operatorUsers);
        viewerUsers = viewerUsers == null ? List.of() : List.copyOf(viewerUsers);
        operatorGroups = operatorGroups == null ? List.of() : List.copyOf(operatorGroups);
    }

    public enum Mode {
        NONE,
        BASIC,
        OIDC
    }
}