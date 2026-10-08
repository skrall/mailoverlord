package org.mailoverlord.server.config;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
 * Exercises the trusted-header chains on a {@link WebMvcTest} slice. The requests act as the
 * proxy: each one states where its connection came from with {@code remoteAddress(...)} and the
 * {@code mailoverlord.security.trusted-proxies} CIDRs say which such statements are believed.
 *
 * <p>The central property is the source check: the identity header is accepted only when the
 * connection itself came from a trusted proxy, and a request carrying the same header from
 * anywhere else is answered 401 rather than authenticated. That is the test that has to exist,
 * and {@link #aHeaderFromOutsideTheTrustedProxies_isRejected} is it: delete the source
 * restriction and only the peer-address line of the other tests keeps it from passing.
 */
@WebMvcTest(controllers = MessageRestController.class,
        properties = {
                "mailoverlord.security.mode=header",
                "mailoverlord.security.header=X-Remote-User",
                "mailoverlord.security.groups-header=X-Forwarded-Groups",
                "mailoverlord.security.operator-groups=mailoverlord-operators",
                "mailoverlord.security.trusted-proxies=10.0.0.0/8,127.0.0.1/32" })
@Import(SecurityConfig.class)
public class TrustedHeaderSecurityConfigTest {

    private static final String TRUSTED = "10.0.0.6";
    private static final String OUTSIDER = "172.16.0.5";

    @MockitoBean
    private MessageService messageService;

    @Autowired
    private MockMvc mockMvc;

    private static final MessageSummary SUMMARY =
            new MessageSummary(1L, "from@example.org", "to@example.org", null, null, 0L, "subject");

    @Test
    void anIdentityHeaderFromATrustedProxy_authenticatesAsThatPrincipal() throws Exception {
        when(messageService.listMessages(any(), any()))
                .thenReturn(new PageResponse<>(List.of(SUMMARY), 0, 25, 1, 1, true, true));

        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .remoteAddress(TRUSTED)
                        .header("X-Remote-User", "alice"))
                .andExpect(status().isOk());
        verify(messageService).listMessages(any(), any());
    }

    @Test
    void aMissingIdentityHeader_fromATrustedProxy_is401() throws Exception {
        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .remoteAddress(TRUSTED))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(
                        "Authentication is required. The reverse proxy must set the "
                                + "X-Remote-User header, and the request must have reached the "
                                + "app from a trusted proxy."));
    }

    @Test
    void aBlankIdentityHeader_isTreatedAsMissing() throws Exception {
        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .remoteAddress(TRUSTED)
                        .header("X-Remote-User", "   "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aHeaderFromOutsideTheTrustedProxies_isRejected() throws Exception {
        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .remoteAddress(OUTSIDER)
                        .header("X-Remote-User", "admin")
                        .header("X-Forwarded-Groups", "mailoverlord-operators"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verify(messageService, never()).listMessages(any(), any());
    }

    @Test
    void aContentlessRequestFromOutsideTheTrustedProxies_isAlso401() throws Exception {
        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .remoteAddress(OUTSIDER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void the401OutsideTheTrustedProxies_issuesNoChallenge() throws Exception {
        mockMvc.perform(get("/messages/list?page={p}&size={s}", 0, 25)
                        .remoteAddress(OUTSIDER))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void theOperatorGroup_inTheGroupsHeader_canRelease() throws Exception {
        when(messageService.releaseMessage(any(), any()))
                .thenReturn(MessageReleaseResponse.of(List.of()));

        mockMvc.perform(post("/messages/release")
                        .remoteAddress(TRUSTED)
                        .header("X-Remote-User", "bob")
                        .header("X-Forwarded-Groups", "mailoverlord-operators")
                        .content("""
                                {"messageIds": [1]}
                                """)
                        .contentType("application/json"))
                .andExpect(status().isOk());
        verify(messageService).releaseMessage(any(), any());
    }

    @Test
    void aNonOperatorGroup_leavesOnlyAViewer() throws Exception {
        mockMvc.perform(post("/messages/release")
                        .remoteAddress(TRUSTED)
                        .header("X-Remote-User", "bob")
                        .header("X-Forwarded-Groups", "developers")
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
    void anAbsentGroupsHeader_leavesOnlyAViewer() throws Exception {
        mockMvc.perform(post("/messages/delete")
                        .remoteAddress(TRUSTED)
                        .header("X-Remote-User", "bob")
                        .content("""
                                {"messageIds": [1]}
                                """)
                        .contentType("application/json"))
                .andExpect(status().isForbidden());
        verify(messageService, never()).deleteMessage(any());
    }

    @Test
    void theOperatorGroup_isMatchedCaseInsensitively() throws Exception {
        when(messageService.releaseMessage(any(), any()))
                .thenReturn(MessageReleaseResponse.of(List.of()));

        mockMvc.perform(post("/messages/release")
                        .remoteAddress(TRUSTED)
                        .header("X-Remote-User", "bob")
                        .header("X-Forwarded-Groups", "Delivery,  MailOverlord-Operators ,qa")
                        .content("""
                                {"messageIds": [1]}
                                """)
                        .contentType("application/json"))
                .andExpect(status().isOk());
    }

    @Test
    void theOpenApiDocument_staysOpenThanAProxyBoundary() throws Exception {
        // The slice does not mount the OpenAPI docs endpoint, so a NotFound rather than a 401 is
        // what proves the outsider got past the boundary chain's permitAll.
        mockMvc.perform(get("/v3/api-docs").remoteAddress(OUTSIDER))
                .andExpect(status().isNotFound());
    }
}