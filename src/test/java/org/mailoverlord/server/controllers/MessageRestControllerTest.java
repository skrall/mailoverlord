package org.mailoverlord.server.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mailoverlord.server.AbstractMailoverlordIntegrationTest;
import org.mailoverlord.server.entities.Message;
import org.springframework.http.MediaType;

/**
 * Message REST Controller Test
 */
class MessageRestControllerTest extends AbstractMailoverlordIntegrationTest {

    @Test
    void listReturnsPageOfSummaries() throws Exception {
        saveMessage("from@test.com", "to@test.com");

        mockMvc.perform(get("/messages/list").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].from").value("from@test.com"))
                .andExpect(jsonPath("$.content[0].to").value("to@test.com"))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].sizeBytes").isNumber())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    /**
     * The list used to return whole entities, which put the full message body on every row.
     * Summaries must keep the body out of the response or a page of large messages is
     * hundreds of megabytes of base64.
     */
    @Test
    void listOmitsTheMessageBody() throws Exception {
        saveMessage("from@test.com", "to@test.com");

        mockMvc.perform(get("/messages/list").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].data").doesNotExist());
    }

    /**
     * Subjects are stored as RFC 2047 encoded words, so the summary has to decode them.
     */
    @Test
    void listDecodesEncodedSubject() throws Exception {
        saveMessage("from@test.com", "to@test.com", "Hello world 😀");

        mockMvc.perform(get("/messages/list").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subject").value("Hello world 😀"));
    }

    @Test
    void listIsSortedNewestFirstByDefault() throws Exception {
        Message older = saveMessage("older@test.com", "to@test.com", "Older");
        Message newer = saveMessage("newer@test.com", "to@test.com", "Newer");
        messageRepository.findById(older.getId()).orElseThrow().setReceivedTimestamp(
                java.time.Instant.parse("2020-01-01T00:00:00Z"));
        messageRepository.findById(newer.getId()).orElseThrow().setReceivedTimestamp(
                java.time.Instant.parse("2024-01-01T00:00:00Z"));
        messageRepository.save(messageRepository.findById(older.getId()).orElseThrow());
        messageRepository.save(messageRepository.findById(newer.getId()).orElseThrow());

        mockMvc.perform(get("/messages/list").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].from").value("newer@test.com"))
                .andExpect(jsonPath("$.content[1].from").value("older@test.com"));
    }

    /**
     * The UI cannot render pagination controls without the page metadata, and Spring Data's
     * web support has to be switched on for the {@code Pageable} argument to bind.
     */
    @Test
    void pageableQueryParametersAreHonoured() throws Exception {
        for (int i = 0; i < 5; i++) {
            saveMessage("from" + i + "@test.com", "to@test.com");
        }

        mockMvc.perform(get("/messages/list").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void detailReturnsTheMessageBody() throws Exception {
        Message message = saveMessage("from@test.com", "to@test.com", "Detail subject");

        mockMvc.perform(get("/messages/{id}", message.getId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(message.getId()))
                .andExpect(jsonPath("$.from").value("from@test.com"))
                .andExpect(jsonPath("$.subject").value("Detail subject"))
                .andExpect(jsonPath("$.body").value("Hi"));
    }

    @Test
    void detailOfUnknownMessageIsNotFound() throws Exception {
        mockMvc.perform(get("/messages/{id}", 999999L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void releaseRequestSucceeds() throws Exception {
        Message message = saveMessage("from@test.com", "to@test.com");

        mockMvc.perform(post("/messages/release")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageIds\": [%d]}".formatted(message.getId())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.successful").value(true));
    }

    @Test
    void deleteRequestRemovesMessage() throws Exception {
        Message message = saveMessage("from@test.com", "to@test.com");

        mockMvc.perform(post("/messages/delete")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageIds\": [%d]}".formatted(message.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(true));

        assertThat(messageRepository.findById(message.getId())).isEmpty();
    }

    /**
     * Both request DTOs default messageIds to an empty list, so a body that misspells the
     * field deserialises cleanly and leaves nothing selected. The service then deletes
     * nothing and the endpoint answered successful: true, which is a lie for a destructive
     * operation and impossible to tell apart from a real delete.
     */
    @Test
    void deleteWithAMisspelledIdFieldIsRejected() throws Exception {
        Message message = saveMessage("from@test.com", "to@test.com");

        mockMvc.perform(post("/messages/delete")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [%d]}".formatted(message.getId())))
                .andExpect(status().isBadRequest());

        // The message must survive: a rejected request is not a licence to delete.
        assertThat(messageRepository.findById(message.getId())).isPresent();
    }

    @Test
    void deleteWithAnEmptyIdListIsRejected() throws Exception {
        mockMvc.perform(post("/messages/delete")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageIds\": []}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * An explicit null used to throw a NullPointerException out of the debug log line,
     * which read getMessageIds().size() before the try block, and surfaced as a 500.
     */
    @Test
    void deleteWithANullIdListIsRejected() throws Exception {
        mockMvc.perform(post("/messages/delete")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageIds\": null}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Release shared the same guardless shape as delete, and the stakes are higher: a
     * silently empty release forwards no mail while reporting that it did.
     */
    @Test
    void releaseWithAMisspelledIdFieldIsRejected() throws Exception {
        mockMvc.perform(post("/messages/release")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [1]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void releaseWithAnEmptyIdListIsRejected() throws Exception {
        mockMvc.perform(post("/messages/release")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageIds\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void releaseWithANullIdListIsRejected() throws Exception {
        mockMvc.perform(post("/messages/release")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageIds\": null}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * A valid delete still deletes, and still answers 200. The rejections above are only
     * meaningful if the happy path is unaffected.
     */
    @Test
    void deleteWithAnUnknownIdStillSucceeds() throws Exception {
        mockMvc.perform(post("/messages/delete")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageIds\": [999999]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(true));
    }

    /**
     * The Vue UI generates its types from this document, so the endpoints it calls have to
     * keep showing up here.
     */
    @Test
    void openApiDocumentDescribesTheApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/messages/list'].get").exists())
                .andExpect(jsonPath("$.paths['/messages/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/messages/delete'].post").exists())
                .andExpect(jsonPath("$.paths['/messages/release'].post").exists());
    }

    /**
     * Data REST used to publish a second, overlapping API at /message. It is gone, and
     * nothing should quietly reintroduce it.
     */
    @Test
    void dataRestRepositoryEndpointIsGone() throws Exception {
        mockMvc.perform(get("/message").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
