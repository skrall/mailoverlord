package org.mailoverlord.server.config;

import java.util.List;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Runtime hints needed to send released mail from a native image.
 *
 * <p>Jakarta Mail does not keep transport instances in the provider; it names the class in the
 * provider file and creates it reflectively:
 * <pre>
 * Class.forName(provider.getClassName())
 *     .getConstructor(Session.class, URLName.class)
 *     .newInstance(session, urlName)
 * </pre>
 *
 * <p>The provider itself is a service loaded at build time, so it and its constructor are in the
 * image. The transport it names is not a service, so its constructor is not registered by
 * anything, and releasing a message fails with:
 * <pre>
 * MissingReflectionRegistrationError: Cannot reflectively invoke constructor
 *     'public org.eclipse.angus.mail.smtp.SMTPTransport(jakarta.mail.Session,
 *     jakarta.mail.URLName)'
 * </pre>
 *
 * <p>Both the plain and the implicit-TLS transport are registered, since the release target's
 * URL decides which one Jakarta Mail asks for.
 */
public class MailRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * The transports Jakarta Mail instantiates by name for the {@code smtp} and {@code smtps}
     * protocols.
     */
    private static final List<String> TRANSPORTS = List.of(
            "org.eclipse.angus.mail.smtp.SMTPTransport",
            "org.eclipse.angus.mail.smtp.SMTPSSLTransport");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String transport : TRANSPORTS) {
            hints.reflection().registerTypeIfPresent(classLoader, transport,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }
    }

    /**
     * The transport classes that need registering, for verification by the tests.
     */
    static List<String> transports() {
        return TRANSPORTS;
    }
}
