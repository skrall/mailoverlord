package org.mailoverlord.server.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mailoverlord.server.AbstractMailoverlordIntegrationTest;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * MessageService Test.
 *
 * <p>Releasing a message sends it back out through the configured SMTP server. In tests
 * that server is mailoverlord itself, so a released message is re-captured, which is
 * exactly what lets these tests assert on the result.
 */
class MessageServiceTest extends AbstractMailoverlordIntegrationTest {

    private static final String FROM = "messageservicetest@email.com";

    @Autowired
    MessageService messageService;

    @BeforeEach
    void sendMessageToCapture() throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        mimeMessage.setFrom(new InternetAddress(FROM));
        mimeMessage.addRecipients(jakarta.mail.Message.RecipientType.TO, "to@email.com");
        mimeMessage.addRecipients(jakarta.mail.Message.RecipientType.CC, "cc@email.com");
        mimeMessage.addRecipients(jakarta.mail.Message.RecipientType.BCC, "bcc@email.com");

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
    }

    @Test
    void releaseWithoutOverrideKeepsAddresses() {
        List<Message> captured = messageRepository.findByFrom(FROM);
        assertThat(captured).as("captured messages").hasSize(1);

        MessageReleaseRequest request = new MessageReleaseRequest();
        request.addMessageId(captured.getFirst().getId());
        messageService.releaseMessage(request);

        assertThat(messageRepository.findByFrom(FROM)).as("re-captured messages").hasSize(2);
    }

    @Test
    void releaseWithOverrideReplacesAddresses() {
        List<Message> captured = messageRepository.findByFrom(FROM);
        assertThat(captured).as("captured messages").hasSize(1);

        MessageReleaseRequest request = new MessageReleaseRequest();
        request.addMessageId(captured.getFirst().getId());
        request.setOverrideFrom(true);
        request.setOverrideFromAddress("override@override.com");
        request.setOverrideTo(true);
        request.setOverrideToAddresses("override@override.com");
        messageService.releaseMessage(request);

        assertThat(messageRepository.findByFrom(FROM)).as("original messages left alone").hasSize(1);
        assertThat(messageRepository.findByFrom("override@override.com")).as("overridden messages").hasSize(1);
    }

    @Test
    void deleteRemovesCapturedMessages() {
        List<Message> captured = messageRepository.findByFrom(FROM);
        assertThat(captured).as("captured messages").hasSize(1);

        MessageDeleteRequest request = new MessageDeleteRequest();
        request.addMessageId(captured.getFirst().getId());
        messageService.deleteMessage(request);

        assertThat(messageRepository.findByFrom(FROM)).as("remaining messages").isEmpty();
    }
}
