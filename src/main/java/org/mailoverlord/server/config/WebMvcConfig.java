package org.mailoverlord.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.data.repository.support.DomainClassConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

/**
 * To configure the conversion service.
 */
public class WebMvcConfig extends WebMvcConfigurationSupport {
    @Bean
    public DomainClassConverter<?> domainClassConverter() {
        return new DomainClassConverter<>(mvcConversionService());
    }
}
