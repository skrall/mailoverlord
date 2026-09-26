package org.mailoverlord.server.message;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
            return null;
        }

        @Override
        public void done() {
            messageRepository.save(message);
            logger.debug("Done.");
        }
    }
}
