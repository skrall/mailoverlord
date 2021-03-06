package org.mailoverlord.server.config;

import org.mailoverlord.server.message.DatabaseMessageHandlerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.subethamail.smtp.server.SMTPServer;

/**
 * Application config.
 */
@Configuration
@ComponentScan(basePackages = {"org.mailoverlord.server.service"})
public class ApplicationConfig {

    @Autowired
    private Environment environment;

    @Bean
    public DatabaseMessageHandlerFactory databaseMessageHandlerFactory() {
        return new DatabaseMessageHandlerFactory();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SMTPServer smtpServer() {
        SMTPServer server = new SMTPServer(databaseMessageHandlerFactory());
        server.setDisableReceivedHeaders(true);
        server.setPort(environment.getProperty("mailoverlord.listen.port", Integer.class, 2025));
        return server;
    }

}
