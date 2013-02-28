package org.mailoverlord.server.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mailoverlord.server.config.ApplicationConfig;
import org.mailoverlord.server.config.DbcpDataSourceConfig;
import org.mailoverlord.server.config.JpaConfig;
import org.mailoverlord.server.config.TestMailSessionConfig;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * MessageService Test.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {JpaConfig.class, DbcpDataSourceConfig.class, TestMailSessionConfig.class,
                                 ApplicationConfig.class})
public class MessageServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(MessageServiceTest.class);

    @Autowired
    MessageService messageService;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    Session session;

    @Before
    public void setup() {
        try {
            MimeMessage mimeMessage = new MimeMessage(session);
            mimeMessage.setFrom(new InternetAddress("messageservicetest@email.com"));
            mimeMessage.addRecipients(javax.mail.Message.RecipientType.TO, "to@email.com");
            mimeMessage.addRecipients(javax.mail.Message.RecipientType.CC,  "cc@email.com");
            mimeMessage.addRecipients(javax.mail.Message.RecipientType.BCC, "bcc@email.com");

            mimeMessage.setSubject("This is a test message");

            Multipart multipart = new MimeMultipart();

            BodyPart textPart = new MimeBodyPart();
            textPart.setText("This is the text message body...");

            BodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent("<HTML><BODY><H4>Large Html</H4></BODY></HTML>", "text/html");

            multipart.addBodyPart(textPart);
            multipart.addBodyPart(htmlPart);

            mimeMessage.setContent(multipart);

            Transport.send(mimeMessage);
        } catch (Throwable t) {
            logger.error("Error while sending message.", t);
            throw new RuntimeException(t);
        }
    }

    @Test
    public void testForwardNoOverride() {
        Iterable<Message> messages = messageRepository.findAll();
        if(messages.iterator().hasNext()) {
            Message message = messages.iterator().next();
            MessageReleaseRequest request = new MessageReleaseRequest();
            request.addMessageId(message.getId());
            messageService.releaseMessage(request);

            List<Message> insertedMessages = messageRepository.findByFrom("messageservicetest@email.com");
            assertEquals("Number of messages not what is expected", 2, insertedMessages.size());
            // TODO - Assert the results are what are expected.
        }
    }

    @Test
    public void testFowardWithOverride() {
        Iterable<Message> messages = messageRepository.findAll();
        if(messages.iterator().hasNext()) {
            Message message = messages.iterator().next();
            MessageReleaseRequest request = new MessageReleaseRequest();
            request.addMessageId(message.getId());
            request.setOverrideFrom(true);
            request.setOverrideFromAddress("override@override.com");
            request.setOverrideTo(true);
            request.setOverrideToAddresses("override@override.com");
            messageService.releaseMessage(request);

            List<Message> insertedMessages = messageRepository.findByFrom("override@override.com");
            assertEquals("Number of messages not what is expected", 1, insertedMessages.size());
            // TODO - Assert the results are what are expected.
        }
    }
}
