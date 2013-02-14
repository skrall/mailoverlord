package org.mailoverlord.server.service;

import org.mailoverlord.server.model.MessageReleaseRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.Message;
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

    @Override
    public void releaseMessage(MessageReleaseRequest request) {
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(request.getMessage().getData());
            javax.mail.Message message = new MimeMessage(session, byteArrayInputStream);

            String fromAddress = request.getMessage().getFrom();
            if(request.isOverrideFrom()) {
                fromAddress = request.getOverrideFromAddress();
            }

            String toAddress = request.getMessage().getTo();
            if(request.isOverrideTo()) {
                toAddress = request.getOverrideToAddresses();
            }
            String[] toAddresses = toAddress.split(",");
            for(String address : toAddresses) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(address));
            }

            message.setFrom(new InternetAddress(fromAddress));
            Transport.send(message);
        } catch(Throwable t) {
            logger.error("Error while releasing message.", t);
            throw new RuntimeException("Error while releasing message.", t);
        }
    }
}