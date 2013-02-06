package org.mailoverlord.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.naming.InitialContext;
import javax.sql.DataSource;

/**
 * Jndi Config.
 */
@Configuration
@Profile("production")
public class JndiDataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(JndiDataSourceConfig.class);

    @Bean
    public DataSource dataSource() {
        DataSource ds = null;
        try {
            InitialContext ic = new InitialContext();
            ds = (DataSource)ic.lookup("java:/comp/env/jdbc/overlord-datasource");
        } catch(Exception e) {
            logger.error("Error while looking up datasource.", e);
        }
        return ds;
    }


}
