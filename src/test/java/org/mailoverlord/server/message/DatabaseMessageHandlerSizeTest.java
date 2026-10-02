package org.mailoverlord.server.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.repositories.MessageRepository;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.TooMuchDataException;

/**
 * Tests for the message size limit.
 *
 * <p>Drives the handler directly with a small limit rather than sending real mail through the
 * SMTP server. The default is 10 MB, and a test that has to push that much through a socket to
 * prove it stops is slow enough that it would not get run.
 */
class DatabaseMessageHandlerSizeTest {

    private static final int LIMIT = 1024;

    private final MessageRepository messageRepository = org.mockito.Mockito.mock(MessageRepository.class);

    private MessageHandler handler(int limit) {
        return new DatabaseMessageHandlerFactory(messageRepository, limit).create(null);
    }

    private static byte[] bytes(int size) {
        return "x".repeat(size).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void acceptsAMessageExactlyAtTheLimit() throws Exception {
        MessageHandler handler = handler(LIMIT);

        handler.data(new ByteArrayInputStream(bytes(LIMIT)));
        handler.done();

        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void acceptsAMessageJustUnderTheLimit() throws Exception {
        MessageHandler handler = handler(LIMIT);

        handler.data(new ByteArrayInputStream(bytes(LIMIT - 1)));
        handler.done();

        verify(messageRepository).save(any(Message.class));
    }

    /**
     * The limit is on the total, not on a chunk, so a sender cannot get past it by sending
     * more than one buffer's worth at a time.
     */
    @Test
    void rejectsAMessageOneByteOverTheLimit() {
        MessageHandler handler = handler(LIMIT);

        assertThatThrownBy(() -> handler.data(new ByteArrayInputStream(bytes(LIMIT + 1))))
                .isInstanceOf(TooMuchDataException.class)
                .hasMessageContaining(String.valueOf(LIMIT));
    }

    /**
     * Larger than the read buffer, so rejection happens on a later chunk rather than the
     * first. LIMIT * 4 is comfortably past the 8 KB read buffer used internally.
     */
    @Test
    void rejectsAMessageManyTimesTheLimit() {
        MessageHandler handler = handler(LIMIT);

        assertThatThrownBy(() -> handler.data(new ByteArrayInputStream(bytes(LIMIT * 4))))
                .isInstanceOf(TooMuchDataException.class);
    }

    /**
     * done() is what persists, and the library only calls it after data() returns. Rejecting
     * without storing is the whole point, so assert the row never arrives.
     */
    @Test
    void doesNotStoreARejectedMessage() throws Exception {
        MessageHandler handler = handler(LIMIT);

        assertThatThrownBy(() -> handler.data(new ByteArrayInputStream(bytes(LIMIT * 2))))
                .isInstanceOf(TooMuchDataException.class);

        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void acceptsAnEmptyMessage() throws Exception {
        MessageHandler handler = handler(LIMIT);

        handler.data(new ByteArrayInputStream(new byte[0]));
        handler.done();

        verify(messageRepository).save(any(Message.class));
    }

    /**
     * The stored bytes are the message, not a prefix of it, so the subject still reads back.
     */
    @Test
    void storesTheMessageItAccepted() throws Exception {
        MessageHandler handler = handler(LIMIT);
        byte[] body = "Subject: Hello\r\n\r\nbody".getBytes(StandardCharsets.UTF_8);

        handler.data(new ByteArrayInputStream(body));
        handler.done();

        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getData()).isEqualTo(body);
        assertThat(captor.getValue().getSubject()).isEqualTo("Hello");
    }
}