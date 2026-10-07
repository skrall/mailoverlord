package org.mailoverlord.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mailoverlord.server.config.ReleaseProperties;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.entities.ReleaseAudit;
import org.mailoverlord.server.model.MessageReleaseOutcome;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.model.MessageReleaseResponse;
import org.mailoverlord.server.model.ReleaseOutcome;
import org.mailoverlord.server.repositories.MessageRepository;
import org.mailoverlord.server.repositories.ReleaseAuditRepository;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

/**
 * How release reports a batch that partly fails.
 *
 * <p>Delivery is one message at a time and reaches a real inbox, so it cannot be undone and it
 * does not stop at the first problem. These tests drive {@link JavaMailSender} directly rather than
 * through the SMTP listener, because the point is what happens when one send in the middle of a
 * batch fails, which a working listener cannot be asked to do.
 *
 * <p>This is also where the release allowlist and the audit trail are tested at one remove from
 * Spring. The recipient refusal behaves differently for the two halves of a release request: an
 * override recipient is known up front and rejects the whole request, while a recipient the
 * stored message already carries can only be known once the message is read, so it fails that one
 * id. The recipient handling itself is covered against real mail in {@code MessageServiceTest},
 * where the re-captured copy shows who a message actually went to.
 */
class MessageServiceReleaseTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private final MessageRepository messageRepository = mock(MessageRepository.class);

    private final ReleaseAuditRepository releaseAuditRepository = mock(ReleaseAuditRepository.class);

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        releaseWithoutAnAllowlist();
    }

    private void releaseWithoutAnAllowlist() {
        messageService = new MessageServiceImpl(mailSender, messageRepository,
                new ReleaseProperties(null), releaseAuditRepository);
    }

    @Test
    void aFailurePartwayThroughStillDeliversTheRest() {
        Message first = storedMessage(1L);
        Message second = storedMessage(2L);
        Message third = storedMessage(3L);
        givenStoredMessages(List.of(1L, 2L, 3L), first, second, third);
        List<MimeMessage> mime = givenMimeMessages(3);
        doThrow(new MailSendException("smtp refused")).when(mailSender).send(mime.get(1));

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L, 2L, 3L),
                "10.0.0.1");

        assertThat(released(response)).as("the two that went out despite the failure")
                .containsExactly(1L, 3L);
        assertThat(failedIds(response)).as("only the one that failed").containsExactly(2L);
        assertThat(errorFor(response, 2L)).isEqualTo("smtp refused");
    }

    @Test
    void theErrorMessageSaysHowFarTheBatchGot() {
        givenStoredMessages(List.of(1L, 2L, 3L),
                storedMessage(1L), storedMessage(2L), storedMessage(3L));
        List<MimeMessage> mime = givenMimeMessages(3);
        doThrow(new MailSendException("smtp refused")).when(mailSender).send(mime.get(1));

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L, 2L, 3L),
                "10.0.0.1");

        assertThat(response.successful()).as("the batch as a whole did not succeed").isFalse();
        assertThat(response.errorMessage())
                .as("so that a caller who only reads this still learns messages went out")
                .isEqualTo("Released 2 of 3 messages; 1 failed.");
    }

    @Test
    void everyRequestedIdIsReportedInTheOrderAskedFor() {
        givenStoredMessages(List.of(3L, 1L, 2L),
                storedMessage(1L), storedMessage(2L), storedMessage(3L));
        givenMimeMessages(3);

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(3L, 1L, 2L),
                "10.0.0.1");

        assertThat(response.outcomes()).extracting(MessageReleaseOutcome::id)
                .as("so the response lines up with what the caller sent")
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    void anIdThatNoLongerExistsIsReportedRatherThanDropped() {
        givenStoredMessages(List.of(1L, 99L), storedMessage(1L));
        givenMimeMessages(1);

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L, 99L),
                "10.0.0.1");

        assertThat(released(response)).containsExactly(1L);
        assertThat(errorFor(response, 99L)).isEqualTo("No message with id 99");
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void aBatchThatFullySucceedsHasNothingToReport() {
        givenStoredMessages(List.of(1L, 2L),
                storedMessage(1L), storedMessage(2L));
        givenMimeMessages(2);

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L, 2L),
                "10.0.0.1");

        assertThat(response.successful()).isTrue();
        assertThat(response.errorMessage()).isNull();
        assertThat(response.outcomes()).allSatisfy(outcome ->
                assertThat(outcome.errorMessage()).isNull());
    }

    @Test
    void onlyTheMessagesThatWentOutAreMarkedAsReleased() {
        Message first = storedMessage(1L);
        Message second = storedMessage(2L);
        givenStoredMessages(List.of(1L, 2L), first, second);
        List<MimeMessage> mime = givenMimeMessages(2);
        doThrow(new MailSendException("smtp refused")).when(mailSender).send(mime.get(1));

        messageService.releaseMessage(requestFor(1L, 2L), "10.0.0.1");

        verify(messageRepository, never()).save(second);
        verify(messageRepository).save(first);
        assertThat(first.getReleasedTimestamp()).as("the one that went out").isNotNull();
        assertThat(second.getReleasedTimestamp()).as("the one that did not").isNull();
    }

    @Test
    void aFailureWithNoMessageOfItsOwnStillExplainsTheOutcome() {
        givenStoredMessages(List.of(1L), storedMessage(1L));
        List<MimeMessage> mime = givenMimeMessages(1);
        doThrow(new IllegalStateException()).when(mailSender).send(mime.getFirst());

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L),
                "10.0.0.1");

        assertThat(errorFor(response, 1L))
                .as("an unexplained failure is the ambiguity this response exists to remove")
                .isEqualTo("IllegalStateException");
    }

    /**
     * An override recipient is known up front, so a refused one rejects the whole request before
     * anything is attempted, and names the address in the 4xx the controller would surface as a
     * problem detail.
     */
    @Test
    void anOverrideRecipientOffTheAllowlistRejectsTheRequestBeforeAnythingIsSent() {
        messageService = messageServiceWithAllowlist("*@example.com");
        givenStoredMessages(List.of(1L), storedMessage(1L));

        MessageReleaseRequest request = requestFor(1L);
        request.setOverrideTo(true);
        request.setOverrideToAddresses("peer@example.com, minion@evilland.test");

        Throwable thrown = catchThrowable(() ->
                messageService.releaseMessage(request, "10.0.0.1"));

        assertThat(thrown)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ResponseStatusException.class::cast)
                .satisfies(exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason())
                            .as("the refused address is named, and it is the second one")
                            .isEqualTo(ReleaseProperties.refusalMessage("minion@evilland.test"));
                });
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(messageRepository, never()).save(any(Message.class));
    }

    /**
     * A recipient the stored message already carries can only be known once the message is read,
     * so refusing it fails that one id and leaves the rest of the batch alone.
     */
    @Test
    void aStoredRecipientOffTheAllowlistFailsJustThatMessage() throws Exception {
        Message refused = storedMessage(1L);
        Message allowed = storedMessage(2L);
        messageService = messageServiceWithAllowlist("*@example.com");
        givenStoredMessages(List.of(1L, 2L), refused, allowed);
        when(mailSender.createMimeMessage(any(InputStream.class)))
                .thenReturn(messageWithRecipients("minion@evilland.test"))
                .thenReturn(messageWithRecipients("peer@example.com"));

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L, 2L),
                "10.0.0.1");

        assertThat(failedIds(response)).as("only the message with the refused recipient")
                .containsExactly(1L);
        assertThat(released(response)).containsExactly(2L);
        assertThat(errorFor(response, 1L))
                .isEqualTo(ReleaseProperties.refusalMessage("minion@evilland.test"));
        verify(mailSender, times(1)).send(any(MimeMessage.class));
        verify(messageRepository, never()).save(refused);
        verify(messageRepository).save(allowed);
    }

    /**
     * The audit row records the batch as it was asked for and where the resolved recipients
     * went, including a recipient the allowlist refused, since that is the case the trail exists
     * to answer questions about.
     */
    @Test
    void theAuditRowRecordsWhatWasAskedForAndWhereItWent() throws Exception {
        givenStoredMessages(List.of(7L), storedMessage(7L));
        when(mailSender.createMimeMessage(any(InputStream.class)))
                .thenReturn(messageWithRecipients("peer@example.com"));

        MessageReleaseRequest request = requestFor(7L);
        request.setOverrideTo(true);
        request.setOverrideToAddresses("boss@example.com");
        messageService.releaseMessage(request, "10.0.0.1");

        ArgumentCaptor<ReleaseAudit> captured = ArgumentCaptor.forClass(ReleaseAudit.class);
        verify(releaseAuditRepository).save(captured.capture());
        ReleaseAudit audit = captured.getValue();
        assertThat(audit.getOutcome()).isEqualTo(ReleaseOutcome.RELEASED);
        assertThat(audit.getSource()).isEqualTo("10.0.0.1");
        assertThat(audit.getMessageIds()).isEqualTo("[7]");
        assertThat(audit.getOverrideTo()).isEqualTo("boss@example.com");
        assertThat(audit.getDestinations())
                .as("the override replaced the stored recipient, so it is what the audit records")
                .isEqualTo("boss@example.com");
    }

    @Test
    void aRefusedBatchIsRecordedAsPartialAndNamesTheRefusedRecipient() throws Exception {
        givenStoredMessages(List.of(1L), storedMessage(1L));
        messageService = messageServiceWithAllowlist("*@example.com");
        when(mailSender.createMimeMessage(any(InputStream.class)))
                .thenReturn(messageWithRecipients("minion@evilland.test"));

        messageService.releaseMessage(requestFor(1L), "10.0.0.1");

        ArgumentCaptor<ReleaseAudit> captured = ArgumentCaptor.forClass(ReleaseAudit.class);
        verify(releaseAuditRepository).save(captured.capture());
        ReleaseAudit audit = captured.getValue();
        assertThat(audit.getOutcome()).isEqualTo(ReleaseOutcome.FAILED);
        assertThat(audit.getDestinations())
                .as("the recipient the allowlist refused still lands in the trail")
                .isEqualTo("minion@evilland.test");
    }

    private MessageServiceImpl messageServiceWithAllowlist(String... patterns) {
        return new MessageServiceImpl(mailSender, messageRepository,
                new ReleaseProperties(List.of(patterns)), releaseAuditRepository);
    }

    private MessageReleaseRequest requestFor(Long... ids) {
        MessageReleaseRequest request = new MessageReleaseRequest();
        for (Long id : ids) {
            request.addMessageId(id);
        }
        return request;
    }

    private void givenStoredMessages(List<Long> requested, Message... found) {
        when(messageRepository.findAllById(requested)).thenReturn(List.of(found));
    }

    /**
     * One distinct MIME message per stored message, handed out in order, so a test can make a
     * chosen send fail without making the rest fail with it.
     */
    private List<MimeMessage> givenMimeMessages(int count) {
        List<MimeMessage> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            created.add(new MimeMessage((Session) null));
        }
        AtomicInteger next = new AtomicInteger();
        when(mailSender.createMimeMessage(any(InputStream.class)))
                .thenAnswer(invocation -> created.get(next.getAndIncrement()));
        return created;
    }

    /**
     * A delivered message with no recipients at all, which nothing therefore resolves against.
     * Used when the allowlist is not what the test is about.
     */
    private static MimeMessage messageWithRecipients(String... to) throws Exception {
        MimeMessage message = new MimeMessage((Session) null);
        if (to.length > 0) {
            message.addRecipients(RecipientType.TO, InternetAddress.parse(String.join(",", to)));
        }
        return message;
    }

    private static Message storedMessage(Long id) {
        Message message = new Message();
        message.setId(id);
        message.setData(("body of " + id).getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private static List<Long> released(MessageReleaseResponse response) {
        return response.outcomes().stream()
                .filter(MessageReleaseOutcome::released)
                .map(MessageReleaseOutcome::id)
                .toList();
    }

    private static List<Long> failedIds(MessageReleaseResponse response) {
        return response.outcomes().stream()
                .filter(outcome -> !outcome.released())
                .map(MessageReleaseOutcome::id)
                .toList();
    }

    private static String errorFor(MessageReleaseResponse response, Long id) {
        return response.outcomes().stream()
                .filter(outcome -> outcome.id().equals(id))
                .map(MessageReleaseOutcome::errorMessage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome reported for id " + id));
    }
}