package org.mailoverlord.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;
import org.mailoverlord.server.entities.Message;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;

/**
 * Tests for the JPA entity native image runtime hints.
 */
class JpaEntityRuntimeHintsTest {

    /**
     * The accessor the native image refused to invoke while loading a message.
     */
    private static final String FAILING_METHOD = "setSizeBytes";

    private RuntimeHints register() {
        RuntimeHints hints = new RuntimeHints();
        new JpaEntityRuntimeHints().registerHints(hints, getClass().getClassLoader());
        return hints;
    }

    private boolean hasCategoryFor(RuntimeHints hints, String className, MemberCategory category) {
        return hints.reflection().typeHints().toList().stream()
                .filter(hint -> hint.getType().getName().equals(className))
                .anyMatch(hint -> hint.getMemberCategories().contains(category));
    }

    @Test
    void registersEveryEntityInTheApplication() {
        assertThat(JpaEntityRuntimeHints.entityClassNames(getClass().getClassLoader()))
                .as("entities found by scanning for @Entity")
                .contains(Message.class.getName(),
                        "org.mailoverlord.server.entities.ReleaseAudit");

        assertThat(register().reflection().typeHints().map(hint -> hint.getType().getName()).toList())
                .as("reflection hints for every scanned entity")
                .containsAll(JpaEntityRuntimeHints.entityClassNames(getClass().getClassLoader()));
    }

    /**
     * The setter is private, so Spring Boot's AOT entity processing does not register it. The
     * hint has to cover declared methods for Hibernate to be allowed to invoke it.
     */
    @Test
    void allowsHibernateToInvokeThePrivateSetterFromTheNativeImageFailure() throws Exception {
        Method setter = Message.class.getDeclaredMethod(FAILING_METHOD, long.class);

        assertThat(Modifier.isPrivate(setter.getModifiers()))
                .as("%s must stay private for the hint to be what makes it invokable", FAILING_METHOD)
                .isTrue();

        assertThat(hasCategoryFor(register(), Message.class.getName(),
                MemberCategory.INVOKE_DECLARED_METHODS))
                .as("declared methods, not just the public accessors Spring already registers")
                .isTrue();
    }

    @Test
    void allowsHibernateToInstantiateAndPopulateEntities() {
        RuntimeHints hints = register();

        assertThat(hasCategoryFor(hints, Message.class.getName(),
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)).isTrue();
        assertThat(hasCategoryFor(hints, Message.class.getName(),
                MemberCategory.ACCESS_DECLARED_FIELDS)).isTrue();
    }
}
