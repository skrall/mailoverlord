package org.mailoverlord.server.message;

import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Message handler that will store messages in database.
 */
public class DatabaseMessageHandlerFactory implements MessageHandlerFactory {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMessageHandlerFactory.class);

    @Autowired
    private MessageRepository messageRepository;

    private Message message = new Message();

    public MessageHandler create(MessageContext ctx) {
        logger.debug("Creating DatabaseMessageHandler.");
        return new DatabaseMessageHandler(ctx);
    }

    public class DatabaseMessageHandler implements MessageHandler {

        private MessageContext ctx;

        public DatabaseMessageHandler(MessageContext ctx) {
            this.ctx = ctx;
        }

        public void from(String from) throws RejectException {
            logger.debug("From: {}", from);
            message.setFrom(from);
        }

        public void recipient(String recipient) throws RejectException {
            logger.debug("Recipient: {}", recipient);
            //message.getTo().add(recipient);
        }

        public void data(InputStream data) throws RejectException, TooMuchDataException, IOException {
            logger.debug("Got Data....");
            // TODO - Use commons-io to copy input stream into byte array.
        }

        public void done() {
            //messageRepository.save(message);
            logger.debug("Done.");
        }
    }
}
