package org.mailoverlord.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.mail.Session;
import javax.naming.InitialContext;

/**
 * Config for Mail Session from JNDI.
 */
@Configuration
@Profile("production")
public class JndiMailSessionConfig {

    private static final Logger logger = LoggerFactory.getLogger(JndiMailSessionConfig.class);

    @Bean
    public Session session() {
        Session session = null;
        try {
            InitialContext ic = new InitialContext();
            session = (Session)ic.lookup("java:comp/env/mail/overlord-mail");
        } catch(Exception e) {
            logger.error("Error while looking up datasource.", e);
        }
        return session;
    }
}
