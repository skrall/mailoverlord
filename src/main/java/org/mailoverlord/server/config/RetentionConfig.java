package org.mailoverlord.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the periodic sweep that discards captured mail beyond the retention cap.
 *
 * <p>Scheduled unconditionally rather than behind a property, so there is one wiring path and
 * the default (keep everything) is decided inside the sweep itself by
 * {@link RetentionProperties#isUnbounded()}. A conditional would mean two configurations to keep
 * honest, and the second one only differs in whether a no-op method is called.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RetentionProperties.class)
public class RetentionConfig {
}
