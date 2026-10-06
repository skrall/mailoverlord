package org.mailoverlord.server.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageDetail;
import org.mailoverlord.server.model.MessageFilter;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.model.MessageReleaseResponse;
import org.mailoverlord.server.model.MessageResponse;
import org.mailoverlord.server.model.MessageSummary;
import org.mailoverlord.server.model.PageResponse;
import org.mailoverlord.server.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Table Controller.
 */
@RestController
@Tag(name = "Messages", description = "Browse and act on captured mail")
public class MessageRestController {

    private static final Logger logger = LoggerFactory.getLogger(MessageRestController.class);

    /**
     * Columns the table is allowed to order by, named for the entity properties behind them.
     */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("receivedTimestamp", "from", "to", "subject");

    private final MessageService messageService;

    public MessageRestController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Lists messages, optionally narrowed by a filter.
     *
     * <p>Each criterion is applied by the database and omitted from the {@code where} clause
     * when absent, so an unfiltered request behaves exactly as it did before filtering existed.
     * The criteria are combined with AND: a message has to satisfy all of them to appear.
     *
     * <p>The timestamps are inclusive at both ends, so {@code receivedFrom} and
     * {@code receivedTo} set to the same instant match a message that arrived exactly then. That
     * suits the way the UI fills the fields in, which is to the resolution the user picked rather
     * than to a whole day.
     *
     * <p>A malformed timestamp is a 400 from the converter rather than a silent no-match, which is
     * the honest answer for a request the server could not understand.
     */
    @Operation(summary = "List captured messages", description = "Returns one page of message "
            + "summaries. Each summary omits the message body; use getMessage for that. "
            + "Sort by receivedTimestamp, from, to or subject. Every filter is optional, "
            + "matching on a case-insensitive substring, and they are combined with AND. "
            + "receivedFrom and receivedTo are inclusive ISO-8601 instants bounding when the "
            + "mail arrived. The total reflects the filter, not the whole mailbox.")
    @ApiResponse(responseCode = "200", description = "A page of matching messages.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = PageResponse.class)))
    @ApiResponse(responseCode = "400", description = "A parameter could not be understood: a sort "
            + "field other than the four above, a filter timestamp that is not an ISO-8601 "
            + "instant, or a page or size outside the accepted range.",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(value = "/messages/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public PageResponse<MessageSummary> getTableData(
            @PageableDefault(size = 25,
                    sort = "receivedTimestamp", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant receivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant receivedTo) {
        return messageService.listMessages(sorted(pageable),
                new MessageFilter(subject, from, to, receivedFrom, receivedTo));
    }

    /**
     * Rejects an unknown sort field, and gives every ordering a tiebreaker.
     *
     * <p>The one piece of request validation here that is not a constraint on a DTO, and
     * deliberately so. A {@code Pageable} is assembled by Spring Data's own argument resolver
     * before any validation runs, and whether a sort name names a real property is a question
     * about this entity rather than about the shape of a request, so there is nothing on a bean
     * to hang the rule from. Everything else the API accepts is declared on the request DTOs; see
     * #18.
     *
     * <p>The sort name is handed straight to Hibernate, which turns it into an ORDER BY on a
     * column. A name that is not a property does not come back as a bad request; it comes
     * back as an unresolved identifier and surfaces as a 500, which reports a client typo as
     * a server fault.
     *
     * <p>Subjects and sender addresses tie constantly and plenty of mail has no subject at
     * all, so on its own a sort leaves tied rows in whatever order the database happens to
     * produce. The table reloads every ten seconds, so between two reads a tie can break
     * differently and a row moves across a page boundary: it reappears on one page having
     * already been shown on another. Falling back to when the mail arrived makes the order
     * total, and puts the newest first, which is what the table shows by default anyway.
     */
    private Pageable sorted(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot sort messages by " + order.getProperty() + ".");
            }
        }
        if (pageable.getSort().getOrderFor("receivedTimestamp") != null) {
            return pageable;
        }
        Sort sort = pageable.getSort().and(Sort.by(Sort.Direction.DESC, "receivedTimestamp"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    @Operation(summary = "Get one message", description = "Returns a single message in full, "
            + "including its text body and the metadata of every part of its MIME structure. "
            + "Attachments are described, not served: a part reports its filename, content type, "
            + "decoded size and disposition, and size is absent when the message did not declare "
            + "one.")
    @ApiResponse(responseCode = "200", description = "The message, in full.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = MessageDetail.class)))
    @ApiResponse(responseCode = "404", description = "No message has that id. It may have been "
            + "deleted, or the id may never have existed.",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "400", description = "The id is not a number.",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(value = "/messages/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageDetail getMessage(@PathVariable Long id) {
        return messageService.getMessage(id);
    }

    @Operation(summary = "Delete messages", description = "Permanently removes the given messages.")
    @ApiResponse(responseCode = "200", description = "Per-id outcome. This reports failures "
            + "in the body rather than with a status code, so a partially successful request is "
            + "still a 200.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "The body cannot be acted on: messageIds "
            + "was absent, misspelled, null or empty, or named more than 2000 messages. Rejected "
            + "rather than reported as a successful no-op, because deleting nothing while claiming "
            + "success reads as though the messages are gone. The limit is the most ids one page "
            + "of the table can hold, which is the most a selection can contain.",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(value = "/messages/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse deleteMessages(
            @Valid @RequestBody MessageDeleteRequest messageDeleteRequest) {
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
    @ApiResponse(responseCode = "200", description = "An outcome per requested id, in the order "
            + "requested. Delivery is not reversible, so a batch can partly succeed: successful is "
            + "true only when every id was released, and the per-id outcomes are what to consult "
            + "before retrying, since retrying the whole batch re-sends what already went out.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = MessageReleaseResponse.class)))
    @ApiResponse(responseCode = "400", description = "The body cannot be acted on: messageIds "
            + "was absent, misspelled, null or empty, or named more than 2000 messages; or an "
            + "override was asked for without naming addresses that parse, which is what the "
            + "substituted recipients or sender would be taken from. Rejected rather than reported "
            + "as a failed release, since nothing was attempted and nothing needs releasing again. "
            + "The limit is the most ids one page of the table can hold, which is the most a "
            + "selection can contain.",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(value = "/messages/release", produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageReleaseResponse releaseMessages(
            @Valid @RequestBody MessageReleaseRequest messageReleaseRequest) {
        logger.debug("Got MessageReleaseRequest, size: {}", messageReleaseRequest.getMessageIds().size());
        try {
            return messageService.releaseMessage(messageReleaseRequest);
        } catch (Throwable t) {
            // The service reports a per-id outcome for anything that goes wrong while delivering.
            // Reaching here means the batch failed before it ran, so every id is reported as
            // unreleased rather than being left out of the response.
            logger.error("Error while trying to release messages.", t);
            return MessageReleaseResponse.noneReleased(messageReleaseRequest.getMessageIds(),
                    t.getMessage());
        }
    }
}
