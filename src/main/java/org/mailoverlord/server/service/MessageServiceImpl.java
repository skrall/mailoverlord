package org.mailoverlord.server.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.mail.Address;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageDetail;
import org.mailoverlord.server.model.MessageFilter;
import org.mailoverlord.server.model.MessagePart;
import org.mailoverlord.server.model.MessageReleaseOutcome;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.model.MessageReleaseResponse;
import org.mailoverlord.server.model.MessageSummary;
import org.mailoverlord.server.model.PageResponse;
import org.mailoverlord.server.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Message Service implementation.
 */
@Service
public class MessageServiceImpl implements MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageServiceImpl.class);

    private final JavaMailSender mailSender;
    private final MessageRepository messageRepository;

    public MessageServiceImpl(JavaMailSender mailSender, MessageRepository messageRepository) {
        this.mailSender = mailSender;
        this.messageRepository = messageRepository;
    }

    @Override
    public MessageReleaseResponse releaseMessage(MessageReleaseRequest request) {
        // One lookup for the batch, then reported in the order asked for. Ids that were not
        // returned are accounted for as failures rather than dropped, so a stale id in the
        // request is visible instead of silently doing nothing.
        Map<Long, Message> stored = messageRepository.findAllById(request.getMessageIds()).stream()
                .collect(Collectors.toMap(Message::getId, Function.identity()));

        List<MessageReleaseOutcome> outcomes = new ArrayList<>();
        for (Long id : request.getMessageIds()) {
            Message databaseMessage = stored.get(id);
            if (databaseMessage == null) {
                outcomes.add(new MessageReleaseOutcome(id, false, "No message with id " + id));
                continue;
            }
            try {
                deliver(databaseMessage, request);
                outcomes.add(new MessageReleaseOutcome(id, true, null));
            } catch (Exception e) {
                // Delivery has already happened for any earlier id, so the batch carries on
                // rather than throwing. Reporting only the first failure would leave the
                // operator unable to tell which messages to retry.
                logger.error("Error while releasing message {}.", id, e);
                outcomes.add(new MessageReleaseOutcome(id, false, describe(e)));
            }
        }
        return MessageReleaseResponse.of(outcomes);
    }

    /**
     * Hands one message to the SMTP server and records that it went.
     *
     * <p>Deliberately fails on its own rather than partway through: the override has to be applied
     * to the MIME message before it is sent, so there is no useful halfway state to recover to.
     */
    private void deliver(Message databaseMessage, MessageReleaseRequest request)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage(
                new ByteArrayInputStream(databaseMessage.getData()));

        if (request.isOverrideFrom()) {
            message.setFrom(new InternetAddress(request.getOverrideFromAddress()));
        }

        if (request.isOverrideTo()) {
            // Drop every original recipient before substituting the new ones.
            message.setRecipients(RecipientType.TO, (Address[]) null);
            message.setRecipients(RecipientType.CC, (Address[]) null);
            message.setRecipients(RecipientType.BCC, (Address[]) null);
            for (String address : request.getOverrideToAddresses().split(",")) {
                message.addRecipient(RecipientType.TO, new InternetAddress(address.trim()));
            }
        }

        mailSender.send(message);
        databaseMessage.setReleasedTimestamp(Instant.now());
        messageRepository.save(databaseMessage);
    }

    /**
     * A null message would leave the outcome looking like a failure with no explanation, which is
     * the ambiguity this response exists to remove.
     */
    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    @Override
    public void deleteMessage(MessageDeleteRequest request) {
        messageRepository.deleteAllById(request.getMessageIds());
    }

    @Override
    public PageResponse<MessageSummary> listMessages(Pageable pageable, MessageFilter filter) {
        return PageResponse.from(messageRepository.findSummaries(
                filter.subject(),
                filter.from(),
                filter.to(),
                filter.receivedFrom(),
                filter.receivedTo(),
                pageable));
    }

    @Override
    public MessageDetail getMessage(Long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No message with id " + id));
        ParsedMessage parsed = parse(message.getData());
        return new MessageDetail(
                message.getId(),
                message.getFrom(),
                message.getTo(),
                message.getReceivedTimestamp(),
                message.getReleasedTimestamp(),
                message.getSubject(),
                parsed.body(),
                parsed.parts());
    }

    /**
     * The text body of a message and every leaf part of its MIME tree.
     *
     * @param body  the text to show, or null when the message has no content at all
     * @param parts the leaf parts in tree order, empty when the message could not be parsed
     */
    private record ParsedMessage(String body, List<MessagePart> parts) {
    }

    /**
     * Reads a captured message once, for both the text body and the part list.
     *
     * <p>Both come out of a single pass over the MIME tree. Parsing the message twice would mean
     * decoding every attachment twice to describe it once.
     *
     * <p>A message that cannot be parsed falls back to its raw bytes as the body, which is what
     * hides nothing from the reader, and reports no parts rather than a partial list that would
     * read as though the message were simpler than it is.
     */
    private ParsedMessage parse(byte[] data) {
        if (data == null) {
            return new ParsedMessage(null, List.of());
        }
        String raw = new String(data, StandardCharsets.UTF_8);
        try {
            PartWalk walk = new PartWalk();
            walk.collect(mailSender.createMimeMessage(new ByteArrayInputStream(data)));
            return new ParsedMessage(walk.body() != null ? walk.body() : raw, walk.parts());
        } catch (Exception e) {
            logger.debug("Could not read the body of a message, falling back to raw content.", e);
            return new ParsedMessage(raw, List.of());
        }
    }

    /**
     * Walks a MIME tree, collecting each leaf part and the first text part along the way.
     *
     * <p>The body is the first {@code text/plain} part found, which is the same rule as before,
     * so a message with only an HTML body still shows its raw MIME: better an honest wall of
     * markup than an empty panel that looks like an empty mail.
     */
    private static final class PartWalk {

        private final List<MessagePart> parts = new ArrayList<>();

        private String body;

        void collect(Part part) throws Exception {
            if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    collect(multipart.getBodyPart(i));
                }
                return;
            }
            parts.add(describe(part));
        }

        /**
         * Describes one leaf part, taking the first {@code text/plain} part as the body.
         *
         * <p>Only a text part is decoded. Asking an attachment for its content would base64-decode
         * up to 10 MB of PDF per part just to learn its filename, so the metadata comes from the
         * headers instead, which is where it lives anyway.
         */
        private MessagePart describe(Part part) throws Exception {
            String text = part.isMimeType("text/plain") && part.getContent() instanceof String found
                    ? found
                    : null;
            boolean displayed = text != null && body == null;
            if (displayed) {
                body = text;
            }
            return new MessagePart(
                    part.getFileName(),
                    contentTypeOf(part),
                    sizeOf(part),
                    part.getDisposition(),
                    displayed);
        }

        List<MessagePart> parts() {
            return List.copyOf(parts);
        }

        String body() {
            return body;
        }
    }

    /**
     * The part's MIME type without its parameters.
     *
     * <p>Falls back to the header as sent when it will not parse. A content type this code
     * cannot understand is still the best description of the part available, and dropping it
     * would leave a row in the list with nothing to identify it by.
     */
    private static String contentTypeOf(Part part) throws MessagingException {
        String declared = part.getContentType();
        try {
            return new ContentType(declared).getBaseType();
        } catch (MessagingException e) {
            // ParseException, which is what an unparseable header raises, extends this.
            return declared;
        }
    }

    /**
     * The decoded size of a part, or null when it reported none.
     *
     * <p>{@link Part#getSize()} answers -1 when the size is unknown. Null rather than -1 keeps
     * that distinct from a part that is genuinely empty.
     */
    private static Long sizeOf(Part part) throws MessagingException {
        int size = part.getSize();
        if (size < 0) {
            return null;
        }
        return Long.valueOf(size);
    }
}
