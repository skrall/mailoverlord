package org.mailoverlord.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;

/**
 * Tests for the native image runtime hints.
 */
class HibernateRuntimeHintsTest {

    /**
     * The implementation JBoss Logging fails to find in the native image build.
     */
    private static final String FAILING_LOGGER = "org.hibernate.jpa.internal.JpaLogger_$logger";

    private RuntimeHints register() {
        RuntimeHints hints = new RuntimeHints();
        new HibernateRuntimeHints().registerHints(hints, getClass().getClassLoader());
        return hints;
    }

    @Test
    void registersTheLoggerImplementationFromTheNativeImageFailure() {
        RuntimeHints hints = register();

        assertThat(hints.reflection().typeHints().map(hint -> hint.getType().getName()).toList())
                .as("the implementation JBoss Logging looks up reflectively for JpaLogger")
                .contains(FAILING_LOGGER);
    }

    @Test
    void registersEveryGeneratedHibernateLogger() {
        RuntimeHints hints = register();

        assertThat(hints.reflection().typeHints().map(hint -> hint.getType().getName()).toList())
                .as("all generated hibernate loggers, not just the one in the error message")
                .filteredOn(name -> name.endsWith("_$logger"))
                .hasSizeGreaterThan(1);
    }

    @Test
    void allowsConstructingLoggers() {
        RuntimeHints hints = register();

        assertThat(hints.reflection().typeHints().toList())
                .filteredOn(hint -> hint.getType().getName().equals(FAILING_LOGGER))
                .allSatisfy(hint -> assertThat(hint.getMemberCategories())
                        .as("member categories for %s", FAILING_LOGGER)
                        .contains(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
    }

    @Test
    void registersLoggerMessageBundles() {
        RuntimeHints hints = register();

        assertThat(hints.resources().resourcePatternHints()
                .flatMap(patternHints -> patternHints.getIncludes().stream())
                .map(pattern -> pattern.getPattern())
                .toList())
                .as("message bundles the generated loggers read their messages from")
                .anyMatch(pattern -> pattern.contains("i18n.properties"));
    }
}
