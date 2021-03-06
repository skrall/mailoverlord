package org.mailoverlord.server.service;

import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;

/**
 * Message Service implementation.
 */
@Service
public class MessageServiceImpl implements MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageServiceImpl.class);

    @Autowired
    private Session session;

    @Autowired
    private MessageRepository messageRepository;

    @Override
    public void releaseMessage(MessageReleaseRequest request) {
        try {
            Iterable<Message> messages = messageRepository.findAll(request.getMessageIds());
            for (Message databaseMessage : messages) {
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(databaseMessage.getData());
                javax.mail.Message message = new MimeMessage(session, byteArrayInputStream);

                if (request.isOverrideFrom()) {
                    message.setFrom(new InternetAddress(request.getOverrideFromAddress()));
                }

                if (request.isOverrideTo()) {
                    message.setRecipients(javax.mail.Message.RecipientType.TO, null);
                    message.setRecipients(javax.mail.Message.RecipientType.CC, null);
                    message.setRecipients(javax.mail.Message.RecipientType.BCC, null);
                    String toAddress = request.getOverrideToAddresses();
                    String[] toAddresses = toAddress.split(",");
                    for (String address : toAddresses) {
                        message.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(address));
                    }
                }

                Transport.send(message);
            }
        } catch (Throwable t) {
            logger.error("Error while releasing message.", t);
            throw new RuntimeException("Error while releasing message.", t);
        }
    }

    @Override
    public void deleteMessage(MessageDeleteRequest request) {
        messageRepository.delete(messageRepository.findAll(request.getMessageIds()));
    }
}
