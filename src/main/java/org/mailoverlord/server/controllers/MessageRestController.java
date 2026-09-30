package org.mailoverlord.server.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
        requireMessageIds(messageDeleteRequest.getMessageIds());
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
        requireMessageIds(messageReleaseRequest.getMessageIds());
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

    /**
     * A request that names no messages is a client mistake, not a successful no-op.
     *
     * <p>Both DTOs default {@code messageIds} to an empty list, so a body with a
     * misspelled field, such as {@code {"ids": [1]}}, deserialises cleanly into an empty
     * list and the service then does nothing. That reported {@code successful: true}
     * while deleting nothing, which is the worst kind of answer for a destructive
     * operation. An explicit {@code null} was worse still: the debug log line read
     * {@code getMessageIds().size()} before the try block, so it threw a
     * NullPointerException that escaped as a 500.
     *
     * <p>Rejects both rather than quietly succeeding, so the caller learns that its
     * request did not do what it said. A 400 is also the honest status: nothing was
     * attempted, so this is not a partial failure of an operation that ran.
     */
    private void requireMessageIds(List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "messageIds must contain at least one message id.");
        }
    }
}
