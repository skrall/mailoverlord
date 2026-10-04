package org.mailoverlord.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.repositories.MessageRepository;
import org.mailoverlord.server.repositories.TestMessageRepository;
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

    /**
     * Test-scope access to the messages the SMTP server captured.
     *
     * <p>Separate from {@link #messageRepository} because the finder it carries is not something
     * the application offers. Anything the application genuinely needs belongs on the production
     * repository; if a query only a test wants, it belongs here.
     */
    @Autowired
    protected TestMessageRepository testMessageRepository;

    @BeforeEach
    void clearMessages() {
        messageRepository.deleteAll();
    }

    /**
     * Stores a message the way the SMTP server would, so that it can be released again.
     */
    protected Message saveMessage(String from, String to) throws MessagingException, IOException {
        return saveMessage(from, to, "Test subject");
    }

    /**
     * Stores a message with an explicit subject. The MIME bytes keep the RFC 2047 encoded
     * form, as the SMTP server produces for a non-ASCII subject, so that the raw data stays
     * realistic; the column gets the decoded text, because that is what the handler writes
     * once it has read the header. Decoding itself is covered where it happens, by sending
     * real mail through the SMTP port rather than by inserting a row directly.
     */
    protected Message saveMessage(String from, String to, String subject)
            throws MessagingException, IOException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        mimeMessage.setFrom(new InternetAddress(from));
        mimeMessage.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        mimeMessage.setSubject(subject, StandardCharsets.UTF_8.name());
        mimeMessage.setText("Hi");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mimeMessage.writeTo(out);

        Message message = new Message();
        message.setFrom(from);
        message.appendTo(to);
        message.setSubject(subject);
        message.setData(out.toByteArray());
        return messageRepository.save(message);
    }

    /**
     * Stores a message that never had a subject, which is what arrives when a sender omits
     * the header. Its own fixture because an absent subject is a value the table has to sort
     * and the API has to carry, not an edge case that can be stood in for by an empty string.
     */
    protected Message saveMessageWithoutSubject(String from, String to)
            throws MessagingException, IOException {
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
