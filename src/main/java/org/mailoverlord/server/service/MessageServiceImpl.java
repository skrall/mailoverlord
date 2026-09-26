package org.mailoverlord.server.service;

import java.io.ByteArrayInputStream;

import jakarta.mail.Address;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
}
