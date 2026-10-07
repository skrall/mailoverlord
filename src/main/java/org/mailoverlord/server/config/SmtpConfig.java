package org.mailoverlord.server.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
    DatabaseMessageHandlerFactory databaseMessageHandlerFactory(MessageRepository messageRepository,
            SmtpProperties properties) {
        return new DatabaseMessageHandlerFactory(messageRepository, properties.maxMessageSize());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    SMTPServer smtpServer(DatabaseMessageHandlerFactory messageHandlerFactory, SmtpProperties properties) {
        // Deliberately not setting the builder's maxMessageSize. The library only consults
        // it when it supplies its own MessageHandlerFactory, and we supply ours, so the knob
        // would be dead config implying a limit that nothing enforces. The real bound is in
        // DatabaseMessageHandlerFactory, driven by the same property.
        SMTPServer.Builder builder = SMTPServer.port(properties.port())
                .messageHandlerFactory(messageHandlerFactory)
                .insertReceivedHeaders(!properties.disableReceivedHeaders());

        String bindAddress = properties.bindAddress();
        if (bindAddress != null && !bindAddress.isBlank()) {
            try {
                builder.bindAddress(InetAddress.getByName(bindAddress));
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(
                        "mailoverlord.smtp.bind-address is not a valid address: " + bindAddress, e);
            }
        }
        return builder.build();
    }
}
