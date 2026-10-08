package org.mailoverlord.server.config;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mailoverlord.server.controllers.MessageRestController;
import org.mailoverlord.server.model.MessageReleaseResponse;
import org.mailoverlord.server.model.MessageSummary;
import org.mailoverlord.server.model.PageResponse;
import org.mailoverlord.server.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the OIDC filter chain on a {@link WebMvcTest} slice, along the lines of
 * {@link SecurityConfigTest}. The {@code mode: none} shared context already proves the rest of
 * the suite is untouched; this slice proves the OIDC behaviour on its own.
 *
 * <p>The role part uses the {@code oidcLogin()} request post-processor rather than a real
 * code exchange with an identity provider, which no test process should ever do; the claim to
 * role mapping itself is unit-tested in {@link OidcRolesMapperTest}. The slice is the wiring:
 * unauthenticated API calls get the 401 with a {@code login-url}, viewers are stopped at release
 * and delete, operators are not, and the chain really does require the CSRF token.
 *
 * <p>CSRF matters here and not under Basic because the OIDC session is a cookie: a state-changing
 * request from another site rides the cookie too, so it must carry a token only an origin page
 * can read. The tests therefore add {@code csrf()} to every POST.
 */
@WebMvcTest(controllers = MessageRestController.class,
        properties = {
                "mailoverlord.security.mode=oidc",
                "mailoverlord.security.roles-claim=groups",
                "mailoverlord.security.operator-groups=mailoverlord-operators",
                // The entry point reads the registration ids to build the provider login URL.
                "spring.security.oauth2.client.registration.okta.client-id=test-client",
                "spring.security.oauth2.client.registration.okta.client-secret=test-secret",
                "spring.security.oauth2.client.registration.okta.provider=okta",
                "spring.security.oauth2.client.provider.okta.issuer-uri=https://example.okta.com/oauth2/default" })
@Import(SecurityConfig.class)
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class OidcSecurityConfigTest {

    @MockitoBean
    private MessageService messageService;

    @Autowired
    private MockMvc mockMvc;

    private static final MessageSummary SUMMARY =
            new MessageSummary(1L, "from@example.org", "to@example.org", null, null, 0L, "subject");

    /**
     * The entries are not from the properties annotation above — that maps to
     * {@code OAuth2ClientProperties} — but a plain Spring registration, which is what the
     * {@code oidcLogin()} configurer needs at chain-build time. Properties and registration
     * name the same "okta" id so the {@code login-url} matches.
     */
    @TestConfiguration
    static class OidcClients {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("okta")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri("https://example.okta.com/oauth2/default/v1/authorize")
                    .tokenUri("https://example.okta.com/oauth2/default/v1/token")
                    .userInfoUri("https://example.okta.com/oauth2/default/v1/userinfo")
                    .userNameAttributeName("sub")
                    .jwkSetUri("https://example.okta.com/oauth2/default/v1/keys")
                    .build());
        }

        @Bean
        InMemoryOAuth2AuthorizedClientService oAuth2AuthorizedClientService(ClientRegistrationRepository registrations) {
            return new InMemoryOAuth2AuthorizedClientService(registrations);
        }
    }

    @Test
    void viewer_canReadTheList() throws Exception {
        when(messageService.listMessages(any(), any()))
                .thenReturn(new PageResponse<>(List.of(SUMMARY), 0, 25, 1, 1, true, true));

        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .with(oidcLogin().idToken(idToken -> idToken.claim("sub", "reader"))
                                .authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void operator_canReadTheList() throws Exception {
        when(messageService.listMessages(any(), any()))
                .thenReturn(new PageResponse<>(List.of(SUMMARY), 0, 25, 1, 1, true, true));

        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .with(oidcLogin().idToken(idToken -> idToken.claim("sub", "operator"))
                                .authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void operator_canRelease() throws Exception {
        when(messageService.releaseMessage(any(), any()))
                .thenReturn(MessageReleaseResponse.of(List.of()));

        mockMvc.perform(post("/messages/release")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                        .with(csrf())
                        .content("""
                                {"messageIds": [1]}
                                """)
                        .contentType("application/json"))
                .andExpect(status().isOk());
    }

    @Test
    void operator_canDelete() throws Exception {
        mockMvc.perform(post("/messages/delete")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                        .with(csrf())
                        .content("""
                                {"messageIds": [1]}
                                """)
                        .contentType("application/json"))
                .andExpect(status().isOk());
        verify(messageService).deleteMessage(any());
    }

    @Test
    void viewer_release_isForbidden() throws Exception {
        mockMvc.perform(post("/messages/release")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
                        .with(csrf())
                        .content("""
                                {"messageIds": [1]}
                                """)
                        .contentType("application/json"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("You are not allowed to do that."));
        verify(messageService, never()).releaseMessage(any(), any());
    }

    @Test
    void viewer_delete_isForbidden() throws Exception {
        mockMvc.perform(post("/messages/delete")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
                        .with(csrf())
                        .content("""
                                {"messageIds": [1]}
                                """)
                        .contentType("application/json"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verify(messageService, never()).deleteMessage(any());
    }

    @Test
    void unauthenticatedApiCall_getsAProblemDetailThatNamesTheLoginUrl() throws Exception {
        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail")
                        .value("Authentication is required. Sign in through the configured "
                                + "identity provider."))
                .andExpect(jsonPath("$.login-url").value("/oauth2/authorization/okta"));
    }

    @Test
    void browserNavigation_isRedirectedToTheProvider() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/okta"));
    }

    @Test
    void aStateChangingRequestNeedsTheCsrfToken() throws Exception {
        mockMvc.perform(post("/messages/release")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                        .content("""
                                {"messageIds": [1]}
                                """)
                        .contentType("application/json"))
                .andExpect(status().isForbidden());
        verify(messageService, never()).releaseMessage(any(), any());
    }

    @Test
    void logout_clearsTheLocalSession() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }
}