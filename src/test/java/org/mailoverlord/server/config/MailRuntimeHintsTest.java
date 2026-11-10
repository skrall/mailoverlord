package org.mailoverlord.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Session;
import jakarta.mail.URLName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;

/**
 * Tests for the mail native image runtime hints.
 */
class MailRuntimeHintsTest {

    private RuntimeHints register() {
        RuntimeHints hints = new RuntimeHints();
        new MailRuntimeHints().registerHints(hints, getClass().getClassLoader());
        return hints;
    }

    @Test
    void registersTheSmtpTransportsJakartaMailInstantiatesByName() {
        assertThat(register().reflection().typeHints().map(hint -> hint.getType().getName()).toList())
                .as("the transports behind the smtp and smtps protocols")
                .contains("org.eclipse.angus.mail.smtp.SMTPTransport",
                        "org.eclipse.angus.mail.smtp.SMTPSSLTransport");
    }

    @Test
    void allowsJakartaMailToConstructEachTransport() {
        assertThat(register().reflection().typeHints().toList())
                .filteredOn(hint -> MailRuntimeHints.transports().contains(hint.getType().getName()))
                .allSatisfy(hint -> assertThat(hint.getMemberCategories())
                        .as("constructor category for %s", hint.getType().getName())
                        .contains(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
    }

    /**
     * Jakarta Mail looks the transport up with
     * {@code Class.forName(name).getConstructor(Session.class, URLName.class)}, so a rename or a
     * signature change has to fail here rather than on the first release in a native image.
     */
    @Test
    void everyNamedTransportStillHasTheConstructorJakartaMailCalls() {
        assertThat(MailRuntimeHints.transports())
                .allSatisfy(name -> assertThat(constructorOf(name))
                        .as("transport class named in MailRuntimeHints")
                        .isNotNull());
    }

    private java.lang.reflect.Constructor<?> constructorOf(String className) {
        try {
            return Class.forName(className, false, getClass().getClassLoader())
                    .getConstructor(Session.class, URLName.class);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
