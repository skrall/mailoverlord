package org.mailoverlord.server.config;

import org.mailoverlord.server.message.DatabaseMessageHandlerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.subethamail.smtp.server.SMTPServer;

/**
 * Application config.
 */
@Configuration
public class ApplicationConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SMTPServer smtpServer() {
        SMTPServer server = new SMTPServer(new DatabaseMessageHandlerFactory());
        server.setPort(2025);
        return server;
    }

}
