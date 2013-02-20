package org.mailoverlord.server.controllers;

import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.repositories.MessageRepository;
import org.mailoverlord.server.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * Table Controller.
 */
@Controller
public class RestController {

    private static final Logger logger = LoggerFactory.getLogger(RestController.class);

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageService messageService;

    @RequestMapping(value = "/messages/list", produces = {"application/xml", "application/json"},
                    method = RequestMethod.GET)
    public @ResponseBody List<Message> getTableData(Pageable pageable) {
        Page<Message> page = messageRepository.findAll(pageable);
        return page.getContent();
    }

    @RequestMapping(value = "/messages/delete", method = RequestMethod.POST)
    public void deleteMessages(@RequestBody MessageDeleteRequest messageDeleteRequest) {
        logger.debug("Got MessageDeleteRequest, size: " + messageDeleteRequest.getMessageIds().size());
        messageService.deleteMessage(messageDeleteRequest);
    }

    @RequestMapping(value = "/messages/release", method = RequestMethod.POST)
    public void releaseMessages(@RequestBody MessageReleaseRequest messageReleaseRequest) {
        logger.debug("Got MessageReleaseRequest, size: " + messageReleaseRequest.getMessageIds().size());
        messageService.releaseMessage(messageReleaseRequest);
    }
}
