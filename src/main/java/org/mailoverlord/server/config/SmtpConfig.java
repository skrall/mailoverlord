package org.mailoverlord.server.config;

import org.mailoverlord.server.message.DatabaseMessageHandlerFactory;
import org.mailoverlord.server.repositories.MessageRepository;
import org.subethamail.smtp.server.SMTPServer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Config for the embedded SMTP server that accepts incoming mail.
 */
@Configuration
@EnableConfigurationProperties(SmtpProperties.class)
public class SmtpConfig {

    @Bean
    DatabaseMessageHandlerFactory databaseMessageHandlerFactory(MessageRepository messageRepository) {
        return new DatabaseMessageHandlerFactory(messageRepository);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    SMTPServer smtpServer(DatabaseMessageHandlerFactory messageHandlerFactory, SmtpProperties properties) {
        SMTPServer server = new SMTPServer(messageHandlerFactory);
        server.setDisableReceivedHeaders(properties.disableReceivedHeaders());
        server.setPort(properties.port());
        return server;
    }
}
