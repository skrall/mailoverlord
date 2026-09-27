package org.mailoverlord.server.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageDetail;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.model.MessageResponse;
import org.mailoverlord.server.model.MessageSummary;
import org.mailoverlord.server.model.PageResponse;
import org.mailoverlord.server.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Table Controller.
 */
@RestController
@Tag(name = "Messages", description = "Browse and act on captured mail")
public class MessageRestController {

    private static final Logger logger = LoggerFactory.getLogger(MessageRestController.class);

    private final MessageService messageService;

    public MessageRestController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "List captured messages", description = "Returns one page of message "
            + "summaries. Each summary omits the message body; use getMessage for that.")
    @GetMapping(value = "/messages/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<MessageSummary> getTableData(@PageableDefault(size = 25,
            sort = "receivedTimestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        return messageService.listMessages(pageable);
    }

    @Operation(summary = "Get one message", description = "Returns a single message in full, "
            + "including its text body.")
    @GetMapping(value = "/messages/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageDetail getMessage(@PathVariable Long id) {
        return messageService.getMessage(id);
    }

    @Operation(summary = "Delete messages", description = "Permanently removes the given messages.")
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

    @Operation(summary = "Release messages", description = "Forwards the given messages to the "
            + "configured SMTP server, optionally overriding the from and to addresses.")
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
