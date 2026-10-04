package org.mailoverlord.server.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.mailoverlord.server.AbstractMailoverlordIntegrationTest;
import org.mailoverlord.server.entities.Message;

/**
 * Test for Database Message Handler Factory
 */
class DatabaseMessageHandlerFactoryTest extends AbstractMailoverlordIntegrationTest {

    private static final String MESSAGE_TEXT = "Hi";
    private static final String FROM = "from@test.com";
    private static final String TO1 = "to@from.com";
    private static final String TO2 = "to2@from.com";
    private static final String TO3 = "to3@from.com";

    @Test
    void simpleMessage() throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        message.setFrom(new InternetAddress(FROM));
        message.addRecipients(jakarta.mail.Message.RecipientType.TO, TO1);
        message.addRecipients(jakarta.mail.Message.RecipientType.TO, TO2);
        message.setText(MESSAGE_TEXT);

        mailSender.send(message);

        List<Message> messages = testMessageRepository.findByFrom(FROM);
        assertThat(messages).as("captured messages").hasSize(1);
        Message databaseMessage = messages.getFirst();
        assertThat(databaseMessage.getTo()).as("to addresses").isEqualTo(TO1 + "," + TO2);
        assertThat(databaseMessage.getFrom()).as("from address").isEqualTo(FROM);
        assertThat(new String(databaseMessage.getData(), StandardCharsets.UTF_8)).as("body").contains(MESSAGE_TEXT);
    }

    @Test
    void mimeMessage() throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        mimeMessage.setFrom(new InternetAddress(FROM));
        mimeMessage.addRecipients(jakarta.mail.Message.RecipientType.TO, TO1);
        mimeMessage.addRecipients(jakarta.mail.Message.RecipientType.CC, TO2);
        mimeMessage.addRecipients(jakarta.mail.Message.RecipientType.BCC, TO3);

        mimeMessage.setSubject("This is a test message");

        Multipart multipart = new MimeMultipart();

        BodyPart textPart = new MimeBodyPart();
        textPart.setText("This is the text message body...");

        BodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent("<HTML><BODY><H4>Large Html</H4></BODY></HTML>", "text/html");

        multipart.addBodyPart(textPart);
        multipart.addBodyPart(htmlPart);

        mimeMessage.setContent(multipart);

        mailSender.send(mimeMessage);

        List<Message> messages = testMessageRepository.findByFrom(FROM);
        assertThat(messages).as("captured messages").hasSize(1);
        Message databaseMessage = messages.getFirst();
        assertThat(databaseMessage.getTo()).as("to addresses").isEqualTo(TO1 + "," + TO2 + "," + TO3);
        assertThat(databaseMessage.getFrom()).as("from address").isEqualTo(FROM);
        assertThat(databaseMessage.getSubject()).as("subject").isEqualTo("This is a test message");
    }

    @Test
    void subjectIsStoredDecoded() throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        message.setFrom(new InternetAddress(FROM));
        message.addRecipients(jakarta.mail.Message.RecipientType.TO, TO1);
        message.setSubject("Ünïcödé 😀 subject", StandardCharsets.UTF_8.name());
        message.setText(MESSAGE_TEXT);

        mailSender.send(message);

        List<Message> messages = testMessageRepository.findByFrom(FROM);
        assertThat(messages).as("captured messages").hasSize(1);
        assertThat(messages.getFirst().getSubject())
                .as("subject, decoded from the encoded word the wire carries")
                .isEqualTo("Ünïcödé 😀 subject");
    }

    @Test
    void messageWithoutASubjectIsStoredWithoutOne() throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        message.setFrom(new InternetAddress(FROM));
        message.addRecipients(jakarta.mail.Message.RecipientType.TO, TO1);
        message.setText(MESSAGE_TEXT);

        mailSender.send(message);

        List<Message> messages = testMessageRepository.findByFrom(FROM);
        assertThat(messages).as("captured messages").hasSize(1);
        assertThat(messages.getFirst().getSubject())
                .as("absent subject stays absent rather than becoming an empty string")
                .isNull();
    }

    @Test
    void overlongSubjectIsTruncatedRatherThanRejected() throws MessagingException {
        String subject = "s".repeat(5000);

        MimeMessage message = mailSender.createMimeMessage();
        message.setFrom(new InternetAddress(FROM));
        message.addRecipients(jakarta.mail.Message.RecipientType.TO, TO1);
        message.setSubject(subject, StandardCharsets.UTF_8.name());
        message.setText(MESSAGE_TEXT);

        mailSender.send(message);

        List<Message> messages = testMessageRepository.findByFrom(FROM);
        assertThat(messages).as("captured messages").hasSize(1);
        assertThat(messages.getFirst().getSubject())
                .as("truncated to the column length")
                .hasSize(4000)
                .startsWith("sss");
    }
}
