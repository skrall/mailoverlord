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
import org.mailoverlord.server.model.MessageDetail;
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
    private static final String NESTED_FROM = "nested@email.com";
    private static final String HTML_ONLY_FROM = "htmlonly@email.com";

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
        List<Message> captured = testMessageRepository.findByFrom(FROM);
        assertThat(captured).as("captured messages").hasSize(1);

        MessageReleaseRequest request = new MessageReleaseRequest();
        request.addMessageId(captured.getFirst().getId());
        messageService.releaseMessage(request);

        assertThat(testMessageRepository.findByFrom(FROM)).as("re-captured messages").hasSize(2);
    }

    /**
     * The recipients the message went out to, read back off the re-captured copy.
     *
     * <p>Asserting only that a message was re-captured does not show who it was sent to, since
     * mailoverlord captures whatever arrives on its own SMTP port. Reading the recipients back is
     * what makes the address handling below observable.
     */
    @Test
    void releaseWithoutOverrideDeliversToTheOriginalRecipients() {
        Message original = testMessageRepository.findByFrom(FROM).getFirst();

        MessageReleaseRequest request = new MessageReleaseRequest();
        request.addMessageId(original.getId());
        messageService.releaseMessage(request);

        Message redelivered = reCapturedCopyOf(original);
        // BCC is absent by design rather than by oversight: it arrived as an envelope recipient,
        // and the SMTP server strips the Bcc header when it stores the message, so there is no
        // longer anything in the stored MIME to re-send it to. The original row still records the
        // envelope, which is why it shows three recipients and this one shows two.
        assertThat(recipientsOf(redelivered)).as("recipients of the re-sent message")
                .containsExactlyInAnyOrder("to@email.com", "cc@email.com");
    }

    @Test
    void releaseWithOverrideReplacesAddresses() {
        List<Message> captured = testMessageRepository.findByFrom(FROM);
        assertThat(captured).as("captured messages").hasSize(1);

        MessageReleaseRequest request = new MessageReleaseRequest();
        request.addMessageId(captured.getFirst().getId());
        request.setOverrideFrom(true);
        request.setOverrideFromAddress("override@override.com");
        request.setOverrideTo(true);
        request.setOverrideToAddresses("override@override.com");
        messageService.releaseMessage(request);

        assertThat(testMessageRepository.findByFrom(FROM)).as("original messages left alone").hasSize(1);
        assertThat(testMessageRepository.findByFrom("override@override.com")).as("overridden messages").hasSize(1);
    }

    /**
     * The original TO, CC and BCC have to be dropped, not merely added to. Keeping any of them
     * delivers the message to real people who were meant to be excluded, and none of the other
     * assertions here would notice.
     */
    @Test
    void releaseWithOverrideDropsEveryOriginalRecipient() {
        Message original = testMessageRepository.findByFrom(FROM).getFirst();
        assertThat(recipientsOf(original)).as("the fixture really does carry TO, CC and BCC")
                .containsExactlyInAnyOrder("to@email.com", "cc@email.com", "bcc@email.com");

        MessageReleaseRequest request = new MessageReleaseRequest();
        request.addMessageId(original.getId());
        request.setOverrideTo(true);
        request.setOverrideToAddresses("only@override.com");
        messageService.releaseMessage(request);

        Message redelivered = reCapturedCopyOf(original);
        assertThat(recipientsOf(redelivered)).as("recipients of the re-sent message")
                .containsExactly("only@override.com");
    }

    /**
     * The re-captured copy of a released message, told apart from the original because releasing
     * does not move the message, it sends a second one back in through the SMTP port.
     */
    /**
     * The recipients a message was delivered to, split out of the comma-separated column.
     */
    private static List<String> recipientsOf(Message message) {
        return message.getTo() == null ? List.of() : List.of(message.getTo().split(","));
    }

    private Message reCapturedCopyOf(Message original) {
        List<Message> copies = testMessageRepository.findByFrom(original.getFrom()).stream()
                .filter(message -> !message.getId().equals(original.getId()))
                .toList();
        assertThat(copies).as("re-captured copy of the released message").hasSize(1);
        return copies.getFirst();
    }

    @Test
    void deleteRemovesCapturedMessages() {
        List<Message> captured = testMessageRepository.findByFrom(FROM);
        assertThat(captured).as("captured messages").hasSize(1);

        MessageDeleteRequest request = new MessageDeleteRequest();
        request.addMessageId(captured.getFirst().getId());
        messageService.deleteMessage(request);

        assertThat(testMessageRepository.findByFrom(FROM)).as("remaining messages").isEmpty();
    }

    @Test
    void bodyOfMultipartMessageIsItsTextPart() {
        List<Message> captured = testMessageRepository.findByFrom(FROM);
        MessageDetail detail = messageService.getMessage(captured.getFirst().getId());

        assertThat(detail.body())
                .as("the text part, not the raw MIME")
                .contains("This is the text message body")
                .doesNotContain("MIME-Version")
                .doesNotContain("Content-Type");
    }

    @Test
    void bodyOfNestedMultipartMessageIsItsTextPart() throws MessagingException {
        MimeMessage nested = mailSender.createMimeMessage();
        nested.setFrom(new InternetAddress(NESTED_FROM));
        nested.addRecipients(jakarta.mail.Message.RecipientType.TO, "to@email.com");
        nested.setSubject("nested multipart");

        BodyPart textPart = new MimeBodyPart();
        textPart.setText("The deeply nested text body.");

        BodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent("<HTML><BODY><P>html</P></BODY></HTML>", "text/html");

        Multipart alternative = new MimeMultipart();
        alternative.addBodyPart(textPart);
        alternative.addBodyPart(htmlPart);

        BodyPart wrapper = new MimeBodyPart();
        wrapper.setContent(alternative);

        Multipart mixed = new MimeMultipart();
        mixed.addBodyPart(wrapper);
        nested.setContent(mixed);
        mailSender.send(nested);

        Message captured = testMessageRepository.findByFrom(NESTED_FROM).getFirst();
        assertThat(messageService.getMessage(captured.getId()).body())
                .as("the text part inside a nested multipart")
                .contains("The deeply nested text body")
                .doesNotContain("MIME-Version");
    }

    @Test
    void bodyOfHtmlOnlyMessageFallsBackToRawMime() throws MessagingException {
        MimeMessage htmlOnly = mailSender.createMimeMessage();
        htmlOnly.setFrom(new InternetAddress(HTML_ONLY_FROM));
        htmlOnly.addRecipients(jakarta.mail.Message.RecipientType.TO, "to@email.com");
        htmlOnly.setSubject("html only");

        BodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent("<HTML><BODY><P>only html here</P></BODY></HTML>", "text/html");
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(htmlPart);
        htmlOnly.setContent(multipart);
        mailSender.send(htmlOnly);

        Message captured = testMessageRepository.findByFrom(HTML_ONLY_FROM).getFirst();
        assertThat(messageService.getMessage(captured.getId()).body())
                .as("no text part exists, so the raw MIME is shown rather than hiding the body")
                .contains("only html here");
    }
}
