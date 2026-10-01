package org.mailoverlord.server.message;

import java.io.ByteArrayInputStream;
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

    private final MessageRepository messageRepository;

    public DatabaseMessageHandlerFactory(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
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
            byte[] dataArray = data.readAllBytes();
            logger.debug("Data: {}", new String(dataArray, StandardCharsets.UTF_8));
            message.setData(dataArray);
            // Pulled out of the MIME here rather than at read time so the table can sort on
            // it: the database cannot read a header out of the raw message, and ordering has
            // to happen there for pagination to be correct.
            message.setSubject(readSubject(dataArray));
            return null;
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
