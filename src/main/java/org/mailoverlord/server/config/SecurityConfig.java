package org.mailoverlord.server.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
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
 * <p>{@code oidc} signs browsers in against an identity provider through the authorization code
 * flow. The SPA only ever holds a session cookie, so there is no token in JavaScript to steal;
 * the provider endpoints are read from the registration's {@code issuer-uri}, so Okta and Entra
 * ID are configuration rather than code. The OIDC chain keeps a session and therefore CSRF
 * protection: the UI echoes the {@code XSRF-TOKEN} cookie back as {@code X-XSRF-TOKEN}, and
 * {@code POST /logout} (with the same token) ends the local session. An OIDC 401 answers the
 * API as a problem detail whose {@code login-url} points at the configured provider, so the SPA
 * can restart the login flow; a browser navigation is redirected to the provider directly.
 *
 * <p>{@code header} mode believes the identity a reverse proxy asserts instead of challenging
 * for credentials at all: the app reads the {@code header} and {@code groups-header} headers,
 * but only when the connection itself came from a {@code trusted-proxies} CIDR — any other
 * request is answered 401 whatever it carries, because a header anyone can send is no proof of
 * anything. Roles come from {@code operator-groups} through the same mapper OIDC uses. The mode
 * has no session, no CSRF, and no login page: sign-in is the edge's job (see #58).
 *
 * <p>The identities of {@code basic} mode are the {@code spring.security.user.*} identity and
 * every name in {@code mailoverlord.security.operator-users} and {@code viewer-users}, all
 * sharing the one {@code spring.security.user.password}. Under {@code oidc}, roles come from the
 * configured or {@code operator-groups} in the {@code roles-claim}. Roles split read from
 * mutation in all three modes: OPERATOR may release and delete, VIEWER may only read.
 */
@Configuration
@EnableWebSecurity
// SecurityProperties would usually come from SecurityAutoConfiguration, which a @WebMvcTest
// slice does not load, so bind it here; the duplication is inert when the auto-configuration
// does run.
@EnableConfigurationProperties({MailoverlordSecurityProperties.class, SecurityProperties.class})
public class SecurityConfig {

    /**
     * Method security is for the authenticated modes only: under {@code none} the {@code release}
     * and {@code delete} endpoints stay exactly as they were, including for test-suite requests
     * that carry no identity. A condition rather than a property-conditional annotation, because
     * the default mode is {@code basic} even when the property is left out entirely.
     */
    @Configuration
    @EnableMethodSecurity
    @Conditional(ModeIsNotNone.class)
    static class MethodSecurityConfiguration {
    }

    /**
     * Each condition reads the raw {@code mailoverlord.security.mode} property. Three booleans
     * cover the modes; anything unrecognised is treated as {@code basic}, matching the default.
     */
    private abstract static class ModeCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String mode = context.getEnvironment().getProperty("mailoverlord.security.mode");
            return matchesMode(mode == null ? "" : mode.trim().toLowerCase());
        }

        protected abstract boolean matchesMode(String mode);
    }

    /** Mode names, or defaults to, {@code basic} (and not {@code oidc} or {@code header}). */
    static final class ModeIsClassic extends ModeCondition {
        @Override
        protected boolean matchesMode(String mode) {
            return !"oidc".equals(mode) && !"header".equals(mode);
        }
    }

    /** Mode is {@code oidc}. */
    static final class ModeIsOidc extends ModeCondition {
        @Override
        protected boolean matchesMode(String mode) {
            return "oidc".equals(mode);
        }
    }

    /** Mode is {@code header}: trust the identity headers of a configured proxy only. */
    static final class ModeIsHeader extends ModeCondition {
        @Override
        protected boolean matchesMode(String mode) {
            return "header".equals(mode);
        }
    }

    /** Mode is anything but {@code none}. */
    static final class ModeIsNotNone extends ModeCondition {
        @Override
        protected boolean matchesMode(String mode) {
            return !"none".equals(mode);
        }
    }

    @Bean
    @Conditional(ModeIsClassic.class)
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
     * The OIDC chain: the id-provider login, a session of the SPA's own, and (because the
     * session is a cookie) CSRF protection against state-changing requests from elsewhere. The
     * CSRF token travels in a readable cookie the UI echoes back, the same negotiation the
     * Angular-and-Spring pairing has used for years.
     */
    @Bean
    @Conditional(ModeIsOidc.class)
    SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http, OidcUserService oidcUserService,
            AuthenticationEntryPoint oidcEntryPoint, AccessDeniedHandler deniedHandler)
            throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .oauth2Login(login -> login.userInfoEndpoint(
                        userInfo -> userInfo.oidcUserService(oidcUserService)))
                .logout(logout -> logout
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(oidcEntryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /**
     * Answers a request that reached a guarded endpoint without credentials as RFC 9457.
     *
     * <p>{@code spring.mvc.problemdetails.enabled} does not reach here: this runs in the filter
     * chain, before any controller can produce a body. The {@code WWW-Authenticate} header the
     * default entry point adds is kept, because it is what makes a top-level navigation pop the
     * browser's Basic prompt. The {@code login-url} property is what the SPA navigates to, so it
     * does not have to know which mode produced the 401.
     */
    @Bean
    AuthenticationEntryPoint problemDetailEntryPoint(JsonMapper jsonMapper) {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"mailoverlord\"");
            writeProblem(jsonMapper, response, HttpStatus.UNAUTHORIZED,
                    "Authentication is required. Sign in with the configured user credentials.",
                    Map.of("login-url", "/login"));
        };
    }

    /**
     * The OIDC answers to an unauthenticated request. An API call (the SPA's {@code fetch}) gets
     * an RFC 9457 401 whose {@code login-url} restarts the provider login, because a redirect the
     * {@code fetch} follows to the provider's origin is a CORS failure. A top-level navigation
     * gets an ordinary redirect to the provider (or, with several registrations, to the login
     * page that lists them).
     */
    @Bean
    @Conditional(ModeIsOidc.class)
    AuthenticationEntryPoint oidcEntryPoint(JsonMapper jsonMapper,
            OAuth2ClientProperties clients) {
        List<String> registrationIds = new ArrayList<>(clients.getRegistration().keySet());
        String loginUrl = registrationIds.size() == 1
                ? "/oauth2/authorization/" + registrationIds.get(0)
                : "/login";
        Map<String, Object> apiProperties = Map.of("login-url", loginUrl);
        return DelegatingAuthenticationEntryPoint.builder()
                .addEntryPointFor(
                        (request, response, exception) -> writeProblem(jsonMapper, response,
                                HttpStatus.UNAUTHORIZED,
                                "Authentication is required. Sign in through the configured "
                                        + "identity provider.",
                                apiProperties),
                        PathPatternRequestMatcher.pathPattern("/messages/**"))
                .defaultEntryPoint(new LoginUrlAuthenticationEntryPoint(loginUrl))
                .build();
    }

    /**
     * The trusted-header chain: it only ever matches a request whose connection came from a
     * {@link TrustedProxyRequestMatcher configured proxy}, and treats the identity headers on
     * those requests as authenticated. Stateless like Basic — the proxy authenticates, the app
     * holds no session of its own.
     */
    @Bean
    @Order(1)
    @Conditional(ModeIsHeader.class)
    SecurityFilterChain trustedHeaderSecurityFilterChain(HttpSecurity http,
            TrustedProxyRequestMatcher trustedProxyMatcher,
            TrustedHeaderAuthenticationFilter trustedHeaderFilter,
            AuthenticationEntryPoint trustedHeaderEntryPoint, AccessDeniedHandler deniedHandler)
            throws Exception {
        http.securityMatcher(trustedProxyMatcher)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(trustedHeaderFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(trustedHeaderEntryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /**
     * The other half of the boundary: any request that a trusted proxy did not make. This chain
     * matches everything else, so it answers 401 whatever the request carries — the identity
     * header is simply never believed here, which is what keeps the mode from being trivially
     * forgeable by anyone who can reach the port directly.
     */
    @Bean
    @Order(2)
    @Conditional(ModeIsHeader.class)
    SecurityFilterChain proxyBoundarySecurityFilterChain(HttpSecurity http,
            TrustedProxyRequestMatcher trustedProxyMatcher,
            AuthenticationEntryPoint trustedHeaderEntryPoint, AccessDeniedHandler deniedHandler)
            throws Exception {
        http.securityMatcher(new NegatedRequestMatcher(trustedProxyMatcher))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(trustedHeaderEntryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    /**
     * The header-mode answer to an unauthenticated request. There is nothing for a browser to be
     * sent to: the edge owns sign-in, so the SPA gets the problem detail and stays put (the client
     * only navigates when the problem detail names a {@code login-url}), and no
     * {@code WWW-Authenticate} is emitted because the app accepts no credentials of its own.
     */
    @Bean
    @Conditional(ModeIsHeader.class)
    AuthenticationEntryPoint trustedHeaderEntryPoint(JsonMapper jsonMapper,
            MailoverlordSecurityProperties properties) {
        return (request, response, exception) -> writeProblem(jsonMapper, response,
                HttpStatus.UNAUTHORIZED,
                "Authentication is required. The reverse proxy must set the "
                        + properties.header() + " header, and the request must have reached the "
                        + "app from a trusted proxy.",
                Map.of());
    }

    /**
     * The source allowlist shared by the two header-mode chains, so "trusted" means exactly the
     * same thing on both sides of the boundary.
     */
    @Bean
    @Conditional(ModeIsHeader.class)
    TrustedProxyRequestMatcher trustedProxyMatcher(MailoverlordSecurityProperties properties) {
        return new TrustedProxyRequestMatcher(properties.trustedProxies());
    }

    /**
     * The pre-auth filter that reads the identity and groups headers into the mapped authorities.
     */
    @Bean
    @Conditional(ModeIsHeader.class)
    TrustedHeaderAuthenticationFilter trustedHeaderFilter(
            MailoverlordSecurityProperties properties) {
        return new TrustedHeaderAuthenticationFilter(properties.header(),
                properties.groupsHeader(), new GroupRolesMapper(properties.operatorGroups()));
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
                HttpStatus.FORBIDDEN, "You are not allowed to do that.", Map.of());
    }

    private static void writeProblem(JsonMapper jsonMapper, HttpServletResponse response,
            HttpStatus status, String detail, Map<String, Object> properties) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            problem.setProperty(entry.getKey(), entry.getValue());
        }
        jsonMapper.writeValue(response.getWriter(), problem);
    }

    /**
     * The OIDC user loader that stamps the group-derived roles on the identity. Only ever
     * reached by the OIDC chain, so it carries the same mode condition.
     */
    @Bean
    @Conditional(ModeIsOidc.class)
    OidcUserService oidcUserService(MailoverlordSecurityProperties properties) {
        return new OidcRolesUserService(
                new OidcRolesMapper(properties.rolesClaim(), properties.operatorGroups()));
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
     * Where a 401 sends the browser under Basic: Basic does not pop its dialog for
     * {@code fetch}, so a top-level navigation to /login is what makes the browser ask instead.
     * The path serves the SPA; a reload after answering re-runs the API calls with the cached
     * credentials attached. Under OIDC the generated provider-chooser page claims the same path
     * at the filter level, which is what lists the configured providers.
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