package org.mailoverlord.server.controllers;

import java.util.List;

import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.model.MessageResponse;
import org.mailoverlord.server.repositories.MessageRepository;
import org.mailoverlord.server.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Table Controller.
 */
@RestController
public class MessageRestController {

    private static final Logger logger = LoggerFactory.getLogger(MessageRestController.class);

    private final MessageRepository messageRepository;
    private final MessageService messageService;

    public MessageRestController(MessageRepository messageRepository, MessageService messageService) {
        this.messageRepository = messageRepository;
        this.messageService = messageService;
    }

    @GetMapping(value = "/messages/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Message> getTableData(Pageable pageable) {
        Page<Message> page = messageRepository.findAll(pageable);
        return page.getContent();
    }

    @PostMapping(value = "/messages/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse deleteMessages(@RequestBody MessageDeleteRequest messageDeleteRequest) {
        logger.debug("Got MessageDeleteRequest, size: {}", messageDeleteRequest.getMessageIds().size());
        MessageResponse response = new MessageResponse();
        try {
            messageService.deleteMessage(messageDeleteRequest);
        } catch (Throwable t) {
            logger.error("Error while deleting messages.", t);
            response.setSuccessful(false);
            response.setErrorMessage(t.getMessage());
        }
        return response;
    }

    @PostMapping(value = "/messages/release", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse releaseMessages(@RequestBody MessageReleaseRequest messageReleaseRequest) {
        logger.debug("Got MessageReleaseRequest, size: {}", messageReleaseRequest.getMessageIds().size());
        MessageResponse response = new MessageResponse();
        try {
            messageService.releaseMessage(messageReleaseRequest);
        } catch (Throwable t) {
            logger.error("Error while trying to release messages.", t);
            response.setSuccessful(false);
            response.setErrorMessage(t.getMessage());
        }
        return response;
    }
}
