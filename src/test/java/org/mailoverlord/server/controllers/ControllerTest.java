package org.mailoverlord.server.controllers;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mailoverlord.server.config.EmbeddedDataSourceConfig;
import org.mailoverlord.server.config.JpaConfig;
import org.mailoverlord.server.config.TestConfig;
import org.mailoverlord.server.config.TestMailSessionConfig;
import org.mailoverlord.server.config.WebConfig;
import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Index Controller Test
 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(
        classes = {WebConfig.class, EmbeddedDataSourceConfig.class, JpaConfig.class, TestMailSessionConfig.class,
                   TestConfig.class})
public class ControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @Before
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }

    @Test
    public void testIndex() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    public void testTable() throws Exception {
        mockMvc.perform(get("/messages/list").accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    public void testReleaseRequest() throws Exception {
        MessageReleaseRequest releaseRequest = new MessageReleaseRequest();
        releaseRequest.addMessageId(1l);
        String jsonData = objectMapper.writeValueAsString(releaseRequest);
        mockMvc.perform(
                post("/messages/release").accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonData))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.successful").value(Boolean.TRUE));
    }

    @Test
    public void testDeleteRequest() throws Exception {
        MessageDeleteRequest deleteRequest = new MessageDeleteRequest();
        deleteRequest.addMessageId(1l);
        String jsonData = objectMapper.writeValueAsString(deleteRequest);
        mockMvc.perform(
                post("/messages/delete").accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonData))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.successful").value(Boolean.TRUE));
    }

}
