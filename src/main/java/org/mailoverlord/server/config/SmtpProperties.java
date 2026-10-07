package org.mailoverlord.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the SMTP server that mailoverlord listens on.
 *
 * @param port                   port to listen on
 * @param disableReceivedHeaders whether to strip Received headers from captured messages
 * @param maxMessageSize         largest message to accept, in bytes. Messages above this are
 *                               refused rather than stored
 */
@ConfigurationProperties("mailoverlord.smtp")
public record SmtpProperties(@DefaultValue("2025") int port,
        @DefaultValue("true") boolean disableReceivedHeaders,
        @DefaultValue("10485760") int maxMessageSize,
        @DefaultValue("127.0.0.1") String bindAddress) {
}