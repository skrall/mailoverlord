package org.mailoverlord.server.service;

import static org.assertj.core.api.Assertions.assertThat;
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

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageReleaseOutcome;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.model.MessageReleaseResponse;
import org.mailoverlord.server.repositories.MessageRepository;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * How release reports a batch that partly fails.
 *
 * <p>Delivery is one message at a time and reaches a real inbox, so it cannot be undone and it
 * does not stop at the first problem. These tests drive {@link JavaMailSender} directly rather than
 * through the SMTP listener, because the point is what happens when one send in the middle of a
 * batch fails, which a working listener cannot be asked to do.
 *
 * <p>The recipient handling itself is covered against real mail in {@code MessageServiceTest}, where
 * the re-captured copy shows who a message actually went to.
 */
class MessageServiceReleaseTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private final MessageRepository messageRepository = mock(MessageRepository.class);

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageServiceImpl(mailSender, messageRepository);
    }

    @Test
    void aFailurePartwayThroughStillDeliversTheRest() {
        Message first = storedMessage(1L);
        Message second = storedMessage(2L);
        Message third = storedMessage(3L);
        givenStoredMessages(List.of(1L, 2L, 3L), first, second, third);
        List<MimeMessage> mime = givenMimeMessages(3);
        doThrow(new MailSendException("smtp refused")).when(mailSender).send(mime.get(1));

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L, 2L, 3L));

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

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L, 2L, 3L));

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

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(3L, 1L, 2L));

        assertThat(response.outcomes()).extracting(MessageReleaseOutcome::id)
                .as("so the response lines up with what the caller sent")
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    void anIdThatNoLongerExistsIsReportedRatherThanDropped() {
        givenStoredMessages(List.of(1L, 99L), storedMessage(1L));
        givenMimeMessages(1);

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L, 99L));

        assertThat(released(response)).containsExactly(1L);
        assertThat(errorFor(response, 99L)).isEqualTo("No message with id 99");
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void aBatchThatFullySucceedsHasNothingToReport() {
        givenStoredMessages(List.of(1L, 2L),
                storedMessage(1L), storedMessage(2L));
        givenMimeMessages(2);

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L, 2L));

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

        messageService.releaseMessage(requestFor(1L, 2L));

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

        MessageReleaseResponse response = messageService.releaseMessage(requestFor(1L));

        assertThat(errorFor(response, 1L))
                .as("an unexplained failure is the ambiguity this response exists to remove")
                .isEqualTo("IllegalStateException");
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