package org.mailoverlord.server.config;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mailoverlord.server.controllers.MessageRestController;
import org.mailoverlord.server.model.MessageReleaseResponse;
import org.mailoverlord.server.model.MessageSummary;
import org.mailoverlord.server.model.PageResponse;
import org.mailoverlord.server.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the filter chain, roles, and the RFC 9457 bodies on a {@link WebMvcTest} slice that
 * mocks the service. A second full context is not an option: it would rebind the SMTP port the
 * shared context already holds. The shared context itself doubles as the {@code mode: none}
 * coverage, since it runs the whole suite unauthenticated.
 *
 * <p>The tests sign in with real Basic credentials rather than {@code @WithMockUser}: the point
 * is that the documented {@code spring.security.user.*} identity is OPERATOR while a name in
 * {@code viewer-users} is not, which only a real login through the configured
 * {@code UserDetailsService} can show.
 */
@WebMvcTest(controllers = MessageRestController.class,
        properties = { "mailoverlord.security.mode=basic", "mailoverlord.security.viewer-users=reader" })
@Import(SecurityConfig.class)
public class SecurityConfigTest {

    @MockitoBean
    private MessageService messageService;

    @Autowired
    private MockMvc mockMvc;

    private static final String OPERATOR = "operator";
    private static final String VIEWER = "reader";

    private static final MessageSummary SUMMARY =
            new MessageSummary(1L, "from@example.org", "to@example.org", null, null, 0L, "subject");

    @Test
    void viewer_canReadTheList() throws Exception {
        when(messageService.listMessages(any(), any()))
                .thenReturn(new PageResponse<>(List.of(SUMMARY), 0, 25, 1, 1, true, true));

        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .with(httpBasic(VIEWER, "test-password")))
                .andExpect(status().isOk());
    }

    @Test
    void operator_canReadTheList() throws Exception {
        when(messageService.listMessages(any(), any()))
                .thenReturn(new PageResponse<>(List.of(SUMMARY), 0, 25, 1, 1, true, true));

        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .with(httpBasic(OPERATOR, "test-password")))
                .andExpect(status().isOk());
    }

    @Test
    void operator_canRelease() throws Exception {
        when(messageService.releaseMessage(any(), any()))
                .thenReturn(MessageReleaseResponse.of(List.of()));

        mockMvc.perform(post("/messages/release")
                        .with(httpBasic(OPERATOR, "test-password"))
                        .content("""
                                {"messageIds": [1]}
                                """)
                        .contentType("application/json"))
                .andExpect(status().isOk());
    }

    @Test
    void operator_canDelete() throws Exception {
        mockMvc.perform(post("/messages/delete")
                        .with(httpBasic(OPERATOR, "test-password"))
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
                        .with(httpBasic(VIEWER, "test-password"))
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
                        .with(httpBasic(VIEWER, "test-password"))
                        .content("""
                                {"messageIds": [1]}
                                """)
                        .contentType("application/json"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verify(messageService, never()).deleteMessage(any());
    }

    @Test
    void unauthenticated_request_getsAProblemDetailAndAChallenge() throws Exception {
        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail")
                        .value("Authentication is required. Sign in with the configured user credentials."))
                .andExpect(jsonPath("$.login-url").value("/login"))
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void wrong_password_isRejected() throws Exception {
        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .with(httpBasic(OPERATOR, "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void anIdentityHeader_isNotTrustedUnlessTheModeIsHeader() throws Exception {
        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .header("X-Remote-User", "admin")
                        .header("X-Forwarded-Groups", "mailoverlord-operators"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
        verify(messageService, never()).listMessages(any(), any());
    }

    @Test
    void login_promptsForAuthenticationSoTheBrowserShowsTheBasicDialog() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void actuatorHealth_needsNoCredentials() throws Exception {
        // The slice mounts no actuator endpoints, so a NotFound rather than a challenge is what
        // proves the request passed the permitAll. Same pattern as the OpenAPI tests.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actuatorInternalEndpoints_rejectAnonymousAndViewer() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
        mockMvc.perform(get("/actuator/env").with(httpBasic(VIEWER, "test-password")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("You are not allowed to do that."));
    }

    @Test
    void actuatorInternalEndpoints_areReachableByAnOperator() throws Exception {
        // 404 rather than a 403: the operator cleared the role guard, and only the slice's
        // missing actuator beans turned it into a NotFound.
        mockMvc.perform(get("/actuator/env").with(httpBasic(OPERATOR, "test-password")))
                .andExpect(status().isNotFound());
    }
}