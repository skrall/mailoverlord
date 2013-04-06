package org.mailoverlord.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.naming.InitialContext;
import javax.sql.DataSource;

/**
 * Jndi Config.
 */
@Configuration
@Profile("production")
public class JndiDataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(JndiDataSourceConfig.class);

    @Autowired
    private Environment environment;

    @Bean
    public DataSource dataSource() {
        DataSource ds = null;
        try {
            InitialContext ic = new InitialContext();
            String dataSourceName = environment.getProperty("overlord-datasource", "jdbc/overlord-datasource");
            ds = (DataSource)ic.lookup(String.format("java:comp/env/%s", dataSourceName));
        } catch(Exception e) {
            logger.error("Error while looking up datasource.", e);
        }
        return ds;
    }


}
