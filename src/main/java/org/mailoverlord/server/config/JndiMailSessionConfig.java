package org.mailoverlord.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.mail.Session;
import javax.naming.InitialContext;

/**
 * Config for Mail Session from JNDI.
 */
@Configuration
@Profile("production")
public class JndiMailSessionConfig {

    private static final Logger logger = LoggerFactory.getLogger(JndiMailSessionConfig.class);

    @Autowired
    private Environment environment;

    @Bean
    public Session session() {
        Session session = null;
        try {
            InitialContext ic = new InitialContext();
            String mailSessionName = environment.getProperty("overlord-mail", "mail/overlord-mail");
            session = (Session)ic.lookup(String.format("java:comp/env/%s", mailSessionName));
        } catch(Exception e) {
            logger.error("Error while looking up mail session.", e);
        }
        return session;
    }
}
