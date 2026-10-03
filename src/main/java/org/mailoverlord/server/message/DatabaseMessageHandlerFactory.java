package org.mailoverlord.server.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeUtility;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;

/**
 * Message handler that will store messages in database.
 */
public class DatabaseMessageHandlerFactory implements MessageHandlerFactory {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMessageHandlerFactory.class);

    private static final int READ_BUFFER_SIZE = 8192;

    private final MessageRepository messageRepository;
    private final int maxMessageSize;

    public DatabaseMessageHandlerFactory(MessageRepository messageRepository, int maxMessageSize) {
        this.messageRepository = messageRepository;
        this.maxMessageSize = maxMessageSize;
    }

    @Override
    public MessageHandler create(MessageContext ctx) {
        logger.debug("Creating DatabaseMessageHandler.");
        return new DatabaseMessageHandler(ctx);
    }

    public class DatabaseMessageHandler implements MessageHandler {

        private final MessageContext ctx;
        private final Message message = new Message();

        public DatabaseMessageHandler(MessageContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void from(String from) throws RejectException {
            logger.debug("From: {}", from);
            message.setFrom(from);
        }

        @Override
        public void recipient(String recipient) throws RejectException {
            logger.debug("Recipient: {}", recipient);
            message.appendTo(recipient);
        }

        @Override
        public String data(InputStream data) throws RejectException, TooMuchDataException, IOException {
            logger.debug("Got Data....");
            byte[] dataArray = readBounded(data);
            message.setData(dataArray);
            // Pulled out of the MIME here rather than at read time so the table can sort on
            // it: the database cannot read a header out of the raw message, and ordering has
            // to happen there for pagination to be correct.
            message.setSubject(readSubject(dataArray));
            return null;
        }

        /**
         * Reads at most {@link #maxMessageSize} bytes, then gives up.
         *
         * <p>This is the only thing bounding a message, and it has to be here rather than on
         * the SMTP server's own {@code maxMessageSize}: the library consults that setting only
         * inside {@code BasicMessageHandlerFactory}, which it installs when no handler factory
         * is supplied. Mailoverlord supplies this one, so the knob would never be read.
         *
         * <p>Reading with {@link InputStream#readAllBytes()} instead would let a single sender
         * on an unauthenticated socket grow the heap until it failed, so the read is counted as
         * it goes rather than checked afterwards.
         *
         * <p>{@link TooMuchDataException} extends {@link IOException} and is not caught by the
         * library's DATA command, so it unwinds to the session, which answers 421 and closes
         * the connection. That is a blunt answer to what RFC 5321 would call a 552, but it is
         * the library's own behaviour for an oversized message: the unread remainder is still
         * on the socket, and answering without draining it would leave the client mid-message.
         */
        private byte[] readBounded(InputStream data) throws IOException, TooMuchDataException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[READ_BUFFER_SIZE];
            int total = 0;
            int read;
            while ((read = data.read(chunk)) != -1) {
                total += read;
                if (total > maxMessageSize) {
                    throw new TooMuchDataException(
                            "message exceeds the " + maxMessageSize + " byte limit");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }

        /**
         * Reads the Subject header, decoding RFC 2047 encoded words so that non-ASCII
         * subjects are stored readable. Uses {@link InternetHeaders} rather than a full parse
         * because this runs once per message on the SMTP thread.
         *
         * <p>A message with no Subject header yields null, which is a real value here: it is
         * what sorts such a message among the others, so it must not be coerced to "".
         */
        private String readSubject(byte[] data) {
            try {
                InternetHeaders headers = new InternetHeaders(new ByteArrayInputStream(data));
                String subject = headers.getHeader("Subject", null);
                return subject == null ? null : MimeUtility.decodeText(subject);
            } catch (Exception e) {
                // A subject we cannot read is not worth losing the rest of the message over.
                logger.debug("Could not read the subject of a message.", e);
                return null;
            }
        }

        @Override
        public void done() {
            messageRepository.save(message);
            logger.debug("Done.");
        }
    }
}
