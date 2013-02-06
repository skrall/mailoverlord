package org.mailoverlord.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseFactory;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Embedded DataSource Config
 */
@Configuration
public class EmbeddedDataSourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedDataSourceConfig.class);

    @Bean(destroyMethod="shutdown")
    public DataSource dataSource() {
        EmbeddedDatabaseFactory factory = new EmbeddedDatabaseFactory();
        factory.setDatabaseName("overlord");
        factory.setDatabaseType(EmbeddedDatabaseType.H2);
        return factory.getDatabase();
    }

}
