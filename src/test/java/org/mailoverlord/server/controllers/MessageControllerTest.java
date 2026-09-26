package org.mailoverlord.server.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.mailoverlord.server.AbstractMailoverlordIntegrationTest;
import org.mailoverlord.server.entities.Message;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Index Controller Test
 */
class MessageControllerTest extends AbstractMailoverlordIntegrationTest {

    @Test
    void indexPageRenders() throws Exception {
        saveMessage("from@test.com", "to@test.com");

        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    /**
     * The index and the JSON endpoint both rely on the {@code Pageable} controller
     * argument, which needs Spring Data's web support to be switched on explicitly.
     */
    @Test
    void pageableQueryParametersAreHonoured() throws Exception {
        for (int i = 0; i < 5; i++) {
            saveMessage("from" + i + "@test.com", "to@test.com");
        }

        mockMvc.perform(get("/messages/list").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/").param("size", "2").param("page", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void listReturnsJson() throws Exception {
        saveMessage("from@test.com", "to@test.com");

        mockMvc.perform(get("/messages/list").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].from").value("from@test.com"));
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
}
