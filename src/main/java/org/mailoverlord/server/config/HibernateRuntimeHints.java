package org.mailoverlord.server.config;

import java.util.List;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Runtime hints needed to run Hibernate in a GraalVM native image.
 *
 * <p>Hibernate declares its logging interfaces with JBoss Logging, whose annotation
 * processor generates a matching implementation class named {@code <Interface>_$logger} for
 * each one. At runtime {@code Logger.getMessageLogger} finds that implementation by name and
 * instantiates it reflectively, so every one of them needs to be present in the image and
 * reachable through its constructor.
 *
 * <p>A native image without these hints fails at startup, or on first use of a code path, with:
 * <pre>
 * java.lang.IllegalArgumentException: Invalid logger interface
 *     org.hibernate.jpa.internal.JpaLogger (implementation not found)
 * </pre>
 *
 * <p>Registering just the one interface from the error message is not enough. The loggers are
 * initialised lazily, so the next untouched one fails instead, and Hibernate adds new ones
 * between releases. This scans hibernate-core for the generated implementations instead of
 * hardcoding a list, so a Hibernate upgrade does not silently reintroduce the failure.
 */
public class HibernateRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Suffix of the implementation the JBoss Logging processor generates for a
     * message logger interface.
     */
    private static final String LOGGER_SUFFIX = "_$logger";

    private static final String LOGGER_PATTERN = "classpath*:org/hibernate/**/*" + LOGGER_SUFFIX + ".class";

    /**
     * Message bundles the generated loggers read their messages from.
     */
    private static final String MESSAGES_PATTERN = "org/hibernate/**/*i18n.properties";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        List<String> loggers = ClassPathScanner.findClassNames(classLoader, LOGGER_PATTERN);
        for (String logger : loggers) {
            hints.reflection().registerTypeIfPresent(classLoader, logger, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }
        hints.resources().registerPattern(MESSAGES_PATTERN);
    }
}
