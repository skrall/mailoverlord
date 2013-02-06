package org.mailoverlord.server.message;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mailoverlord.server.config.ApplicationConfig;
import org.mailoverlord.server.config.EmbeddedDataSourceConfig;
import org.mailoverlord.server.config.JpaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.subethamail.smtp.client.SmartClient;
import org.subethamail.smtp.server.SMTPServer;

import java.io.IOException;

/**
 * Test for Database Message Handler Factory
 */
@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(classes={JpaConfig.class, EmbeddedDataSourceConfig.class, ApplicationConfig.class})
@ContextConfiguration(classes={ApplicationConfig.class})
public class DatabaseMessageHandlerFactoryTest {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMessageHandlerFactoryTest.class);

    @Autowired
    SMTPServer server;

    @Test
    public void testSimpleMessage() {
        String message = "Hi";
        try {
            SmartClient client = new SmartClient("localhost", 2025, "test.com");
            client.from("from@test.com");
            client.to("to@from.com");
            client.to("to2@from.com");
            client.dataStart();
            client.dataWrite(message.getBytes(), message.getBytes().length);
            client.dataEnd();
            client.quit();
        } catch (IOException e) {
            logger.error("Error while trying to send simple message.", e);
        }
    }


}
