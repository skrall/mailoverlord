package org.mailoverlord.server;

import org.mailoverlord.server.config.ApiModelRuntimeHints;
import org.mailoverlord.server.config.HibernateRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Mailoverlord entry point.
 */
@SpringBootApplication
@ImportRuntimeHints({ HibernateRuntimeHints.class, ApiModelRuntimeHints.class })
public class MailoverlordApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailoverlordApplication.class, args);
    }
}
