package org.mailoverlord.server;

import org.mailoverlord.server.config.HibernateRuntimeHints;
import org.mailoverlord.server.config.WebUiRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Mailoverlord entry point.
 */
@SpringBootApplication
@ImportRuntimeHints({ HibernateRuntimeHints.class, WebUiRuntimeHints.class })
public class MailoverlordApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailoverlordApplication.class, args);
    }
}
