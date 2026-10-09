package org.mailoverlord.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mailoverlord.server.config.SmtpProperties;
import org.subethamail.smtp.server.SMTPServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards the loopback defaults the security workstream sits on.
 *
 * <p>Exposing the web and SMTP listeners on all interfaces is the default an install can miss
 * turning off, so the default is the safe value and opening up is the explicit act. This test is
 * the tripwire for both halves:
 *
 * <ul>
 *   <li>the configured defaults in {@code application.yml}, read from disk rather than from the
 *       running context, because the test-classpath YAML deliberately does not configure them and
 *       so cannot prove what ships;</li>
 *   <li>the live server, which asserts what the config actually produced at bind time.</li>
 * </ul>
 */
class LoopbackDefaultsTest extends AbstractMailoverlordIntegrationTest {

    /** The tested binder {@link SmtpProperties} defaults to when the config says nothing. */
    private static final String LOOPBACK = "127.0.0.1";

    private static final Path CONFIG = Path.of("src", "main", "resources", "application.yml");

    private static final Yaml YAML = new Yaml();

    @Autowired
    SMTPServer smtpServer;

    @Test
    @SuppressWarnings("unchecked")
    void configuredDefaultsBindBothListenersToLoopback() throws IOException {
        Map<String, Object> root = YAML.load(Files.readString(CONFIG));
        Map<String, Object> server = (Map<String, Object>) root.get("server");
        Map<String, Object> smtp = (Map<String, Object>)
                ((Map<String, Object>) root.get("mailoverlord")).get("smtp");

        assertThat(server)
                .as("the web listener must default to loopback")
                .containsEntry("address", LOOPBACK);
        assertThat(smtp)
                .as("the SMTP listener must default to loopback")
                .containsEntry("bind-address", LOOPBACK);
    }

    @Test
    @SuppressWarnings("unchecked")
    void managementPlaneDefaultsToItsOwnLoopbackPort() throws IOException {
        Map<String, Object> root = YAML.load(Files.readString(CONFIG));
        Map<String, Object> managementServer = (Map<String, Object>)
                ((Map<String, Object>) root.get("management")).get("server");

        assertThat(managementServer)
                .as("the management plane must bind to loopback like the other listeners")
                .containsEntry("address", LOOPBACK)
                .as("and live on its own port, separate from the application port")
                .containsEntry("port", 8090);
    }

    @Test
    void smtpServerIsBoundToLoopbackAtStartup() {
        assertThat(smtpServer.getBindAddress())
                .as("what the SMTP server actually bound to")
                .hasValueSatisfying(address ->
                        assertThat(address.isLoopbackAddress()).isTrue());
    }
}