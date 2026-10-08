package org.mailoverlord.server.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authentication for the whole application, selected by {@code mailoverlord.security.mode}.
 *
 * <p>{@code none} reproduces the pre-security behaviour, which is what the existing tests run
 * under. {@code basic} requires HTTP Basic on every request except the OpenAPI document (whose
 * schema is not data), answers 401/403 as RFC 9457 problem details, and does so without a
 * session: Basic carries the credential on every request, so there is no server-side session to
 * protect or invalidate.
 *
 * <p>The identity store is deliberately small: the {@code spring.security.user.*} identity and
 * every name in {@code mailoverlord.security.operator-users} and {@code viewer-users}, all
 * sharing the one {@code spring.security.user.password}. Roles split read from mutation:
 * OPERATOR may release and delete, VIEWER may only read.
 */
@Configuration
@EnableWebSecurity
// SecurityProperties would usually come from SecurityAutoConfiguration, which a @WebMvcTest
// slice does not load, so bind it here; the duplication is inert when the auto-configuration
// does run.
@EnableConfigurationProperties({MailoverlordSecurityProperties.class, SecurityProperties.class})
public class SecurityConfig {

    /**
     * Method security is only for {@code basic}: under {@code none} the {@code release} and
     * {@code delete} endpoints stay exactly as they were, including for test-suite requests
     * that carry no identity. A condition rather than a property-conditional annotation,
     * because the default mode is {@code basic} even when the property is left out entirely.
     */
    @Configuration
    @EnableMethodSecurity
    @Conditional(ModeIsBasic.class)
    static class MethodSecurityConfiguration {
    }

    /**
     * Whether {@code mailoverlord.security.mode} names (or defaults to) {@code basic}.
     */
    static final class ModeIsBasic implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String mode = context.getEnvironment().getProperty("mailoverlord.security.mode");
            return mode == null || mode.isBlank() || "basic".equalsIgnoreCase(mode.trim());
        }
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            MailoverlordSecurityProperties properties, AuthenticationEntryPoint entryPoint,
            AccessDeniedHandler deniedHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                // Connection-based authentication has no ambient credential to abuse, and it
                // removes the login-form machinery that a session would drag in with it.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));

        if (properties.mode() == MailoverlordSecurityProperties.Mode.NONE) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        } else {
            http.httpBasic(Customizer.withDefaults())
                    .authorizeHttpRequests(authorize -> authorize
                            // The OpenAPI contract is a description of the API, not the data it
                            // guards, and the UI build regenerates its types from it.
                            .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                            .anyRequest().authenticated());
        }
        return http.build();
    }

    /**
     * Answers a request that reached a guarded endpoint without credentials as RFC 9457.
     *
     * <p>{@code spring.mvc.problemdetails.enabled} does not reach here: this runs in the filter
     * chain, before any controller can produce a body. The {@code WWW-Authenticate} header the
     * default entry point adds is kept, because it is what makes a top-level navigation pop the
     * browser's Basic prompt.
     */
    @Bean
    AuthenticationEntryPoint problemDetailEntryPoint(JsonMapper jsonMapper) {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"mailoverlord\"");
            writeProblem(jsonMapper, response, HttpStatus.UNAUTHORIZED,
                    "Authentication is required. Sign in with the configured user credentials.");
        };
    }

    /**
     * Answers an authenticated user who reached an endpoint they may not use as RFC 9457.
     *
     * <p>Raised by method security on {@code release} and {@code delete} for a VIEWER, and
     * surfaced here by the exception translation filter rather than as a generic 500.
     */
    @Bean
    AccessDeniedHandler problemDetailDeniedHandler(JsonMapper jsonMapper) {
        return (request, response, exception) -> writeProblem(jsonMapper, response,
                HttpStatus.FORBIDDEN, "You are not allowed to do that.");
    }

    private static void writeProblem(JsonMapper jsonMapper, HttpServletResponse response,
            HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(),
                ProblemDetail.forStatusAndDetail(status, detail));
    }

    /**
     * Encodes the shared {@code spring.security.user.password}. Spring Security 7 removed the
     * default encoder, so without an explicit one every {@code User} built here would refuse to
     * load.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * The identities of the application, all sharing {@code spring.security.user.password}.
     *
     * <p>The {@code spring.security.user.*} identity is OPERATOR, matching the intent that the
     * documented user can run the whole tool. Named users opt into less or more by the list they
     * appear in; an OPERATOR also holds VIEWER, since releasing into an inbox presupposes being
     * able to read what the tool is releasing.
     */
    @Bean
    UserDetailsService userDetailsService(SecurityProperties springUser,
            MailoverlordSecurityProperties properties, PasswordEncoder encoder) {
        String encodedPassword = encoder.encode(springUser.getUser().getPassword());
        Set<String> names = new LinkedHashSet<>();
        for (String operator : properties.operatorUsers()) {
            names.add(operator.trim().toLowerCase());
        }
        for (String viewer : properties.viewerUsers()) {
            names.add(viewer.trim().toLowerCase());
        }
        User.UserBuilder builder = User.withUsername(springUser.getUser().getName())
                .password(encodedPassword)
                .roles("OPERATOR", "VIEWER");
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager(builder.build());
        for (String name : names) {
            boolean operator = properties.operatorUsers().stream()
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .anyMatch(name::equals);
            User.UserBuilder user = User.withUsername(name)
                    .password(encodedPassword);
            if (operator) {
                user.roles("OPERATOR", "VIEWER");
            } else {
                user.roles("VIEWER");
            }
            manager.createUser(user.build());
        }
        return manager;
    }

    /**
     * Where the UI sends the browser when the API answers 401: Basic does not pop its dialog for
     * {@code fetch}, so a top-level navigation to /login is what makes the browser ask instead.
     * The path serves the SPA; a reload after answering re-runs the API calls with the cached
     * credentials attached.
     */
    @Bean
    WebMvcConfigurer loginView() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addViewController("/login").setViewName("forward:/index.html");
            }
        };
    }
}