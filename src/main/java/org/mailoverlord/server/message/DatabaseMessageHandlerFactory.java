package org.mailoverlord.server.message;

import org.apache.commons.io.IOUtils;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Message handler that will store messages in database.
 */
public class DatabaseMessageHandlerFactory implements MessageHandlerFactory {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMessageHandlerFactory.class);

    @Autowired
    private MessageRepository messageRepository;

    public MessageHandler create(MessageContext ctx) {
        logger.debug("Creating DatabaseMessageHandler.");
        return new DatabaseMessageHandler(ctx);
    }

    public class DatabaseMessageHandler implements MessageHandler {

        private MessageContext ctx;
        private Message message = new Message();

        public DatabaseMessageHandler(MessageContext ctx) {
            this.ctx = ctx;
        }

        public void from(String from) throws RejectException {
            logger.debug("From: {}", from);
            message.setFrom(from);
        }

        public void recipient(String recipient) throws RejectException {
            logger.debug("Recipient: {}", recipient);
            message.appendTo(recipient);
        }

        public void data(InputStream data) throws RejectException, IOException {
            logger.debug("Got Data....");
            byte[] dataArray = IOUtils.toByteArray(data);
            logger.debug("Data: {}", new String(dataArray));
            message.setData(dataArray);
        }

        public void done() {
            messageRepository.save(message);
            logger.debug("Done.");
        }
    }
}
