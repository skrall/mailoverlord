package org.mailoverlord.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Makes the release settings available.
 *
 * <p>There is no dedicated bean to create: the properties need only to be registered so that the
 * service can inject them. The class exists to carry that registration, on the same pattern as
 * {@link SmtpConfig} does for {@link SmtpProperties}.
 */
@Configuration
@EnableConfigurationProperties(ReleaseProperties.class)
public class ReleaseConfig {
}