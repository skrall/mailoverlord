package org.mailoverlord.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.repositories.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for the integration tests.
 *
 * <p>Every test shares a single cached application context, which matters here: the
 * embedded SMTP server binds a fixed port on startup, so a second context would fail
 * to start. Because this class is the single place the annotations live, all
 * subclasses are guaranteed to resolve to the same context.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractMailoverlordIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JavaMailSender mailSender;

    @Autowired
    protected MessageRepository messageRepository;

    @BeforeEach
    void clearMessages() {
        messageRepository.deleteAll();
    }

    /**
     * Stores a message the way the SMTP server would, so that it can be released again.
     */
    protected Message saveMessage(String from, String to) throws MessagingException, IOException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        mimeMessage.setFrom(new InternetAddress(from));
        mimeMessage.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        mimeMessage.setText("Hi");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mimeMessage.writeTo(out);

        Message message = new Message();
        message.setFrom(from);
        message.appendTo(to);
        message.setData(out.toByteArray());
        return messageRepository.save(message);
    }
}
