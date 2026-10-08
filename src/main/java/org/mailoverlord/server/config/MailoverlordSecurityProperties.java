package org.mailoverlord.server.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for request authentication.
 *
 * <p>{@code mode} picks which filter chain guards the API: {@code none} keeps the pre-security
 * behaviour and is what the tests run under, {@code basic} requires HTTP Basic on every request.
 * Later mechanisms (OIDC, a trusted forwarded header) add their own chains alongside rather than
 * replacing this select, so the mechanisms compose. See #56.
 *
 * <p>{@code operatorUsers} and {@code viewerUsers} name identities by user name, sharing one
 * {@code spring.security.user.password}. OPERATOR may release and delete; VIEWER may only read.
 * The {@code spring.security.user.*} identity is always an OPERATOR.
 *
 * @param mode          which authentication mechanism to enforce
 * @param operatorUsers the user names that may release and delete
 * @param viewerUsers   the user names that may only read
 */
@ConfigurationProperties("mailoverlord.security")
public record MailoverlordSecurityProperties(
        @DefaultValue("basic") Mode mode,
        List<String> operatorUsers,
        List<String> viewerUsers) {

    public MailoverlordSecurityProperties {
        operatorUsers = operatorUsers == null ? List.of() : List.copyOf(operatorUsers);
        viewerUsers = viewerUsers == null ? List.of() : List.copyOf(viewerUsers);
    }

    public enum Mode {
        NONE,
        BASIC
    }
}