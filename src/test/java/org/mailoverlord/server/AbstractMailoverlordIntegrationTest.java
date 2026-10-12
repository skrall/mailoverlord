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
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for the integration tests.
 *
 * <p>Every test shares a single cached application context, which matters here: the
 * embedded SMTP server binds a fixed port on startup, so a second context would fail
 * to start. Because this class is the single place the annotations live, all
 * subclasses are guaranteed to resolve to the same context.
 *
 * <p>{@code @Transactional} is here because {@code DATA} is a {@code @Lob}, and on
 * PostgreSQL that maps to a large object rather than to bytes stored in the row. Reading
 * one back needs the JDBC session that opened it, so a test that loads a {@code Message}
 * entity with the transaction already closed gets {@code Unable to access lob stream}.
 * That is not hypothetical: every test in this hierarchy that asserted on a message body
 * failed against PostgreSQL until this was added, and nothing caught it because the suite
 * had only ever been run against H2, where the LOB travels inside the row and needs no
 * session.
 *
 * <p>It is a test-only concern. The application reads messages through
 * {@code MessageRepository.findSummaries}, which projects and never touches {@code DATA},
 * and through the service methods, which are transactional in their own right. So the
 * production paths were always fine; only the tests were reading a large object out of
 * band.
 *
 * <p>A side effect worth naming: each test now rolls back rather than relying on
 * {@link #clearMessages()} to tidy up. That cleanup is left in place and is harmless, and
 * is still what keeps a test that fails mid-way from leaking into the next one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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
        return storeMessage(from, to, subject);
    }

    /**
     * Stores a message that never had a subject, which is what arrives when a sender omits
     * the header. Its own fixture because an absent subject is a value the table has to sort
     * and the API has to carry, not an edge case that can be stood in for by an empty string.
     */
    protected Message saveMessageWithoutSubject(String from, String to)
            throws MessagingException, IOException {
        return storeMessage(from, to, null);
    }

    /**
     * The body shared by both fixtures, which differed only in whether they set a subject.
     *
     * <p>A null subject leaves the header off the MIME message entirely rather than setting it
     * to an empty string, because that is what a sender who omitted it produces. The column
     * takes the value as given, where null is already what an absent subject stores as.
     */
    private Message storeMessage(String from, String to, String subject)
            throws MessagingException, IOException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        mimeMessage.setFrom(new InternetAddress(from));
        mimeMessage.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        if (subject != null) {
            mimeMessage.setSubject(subject, StandardCharsets.UTF_8.name());
        }
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
}
