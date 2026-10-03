package org.mailoverlord.server.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import jakarta.mail.Address;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageDetail;
import org.mailoverlord.server.model.MessageReleaseRequest;
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
    public void releaseMessage(MessageReleaseRequest request) {
        try {
            for (Message databaseMessage : messageRepository.findAllById(request.getMessageIds())) {
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
            }
        } catch (Throwable t) {
            logger.error("Error while releasing message.", t);
            throw new RuntimeException("Error while releasing message.", t);
        }
    }

    @Override
    public void deleteMessage(MessageDeleteRequest request) {
        messageRepository.deleteAllById(request.getMessageIds());
    }

    @Override
    public PageResponse<MessageSummary> listMessages(Pageable pageable) {
        return PageResponse.from(messageRepository.findSummaries(pageable));
    }

    @Override
    public MessageDetail getMessage(Long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No message with id " + id));
        return new MessageDetail(
                message.getId(),
                message.getFrom(),
                message.getTo(),
                message.getReceivedTimestamp(),
                message.getSubject(),
                extractBody(message.getData()));
    }

    private String extractBody(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            String text = firstTextPart(mailSender.createMimeMessage(new ByteArrayInputStream(data)));
            return text != null ? text : new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.debug("Could not read the body of a message, falling back to raw content.", e);
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    /**
     * Returns the first text/plain body found, descending into multipart messages. Messages
     * with only an HTML body have no text part, in which case the caller falls back to the
     * raw MIME so nothing is hidden from the reader.
     */
    private String firstTextPart(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content instanceof String text ? text : null;
        }
        if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = firstTextPart(multipart.getBodyPart(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
