package org.mailoverlord.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;

/**
 * Tests for the Bean Validation native image runtime hints.
 */
class ValidationRuntimeHintsTest {

    /**
     * The validator Hibernate Validator could not construct in the native image.
     */
    private static final String FAILING_VALIDATOR =
            "org.mailoverlord.server.model.UsableOverridesValidator";

    private RuntimeHints register() {
        RuntimeHints hints = new RuntimeHints();
        new ValidationRuntimeHints().registerHints(hints, getClass().getClassLoader());
        return hints;
    }

    @Test
    void findsTheValidatorFromTheNativeImageFailure() {
        assertThat(ValidationRuntimeHints.validatorClassNames(getClass().getClassLoader()))
                .as("constraint validators found by scanning for the interface")
                .contains(FAILING_VALIDATOR);
    }

    @Test
    void registersEveryValidatorInTheApplication() {
        assertThat(register().reflection().typeHints().map(hint -> hint.getType().getName()).toList())
                .as("reflection hints for every scanned validator")
                .containsAll(ValidationRuntimeHints.validatorClassNames(getClass().getClassLoader()));
    }

    @Test
    void allowsHibernateValidatorToConstructValidators() {
        RuntimeHints hints = register();

        assertThat(hints.reflection().typeHints().toList())
                .filteredOn(hint -> hint.getType().getName().equals(FAILING_VALIDATOR))
                .allSatisfy(hint -> assertThat(hint.getMemberCategories())
                        .as("constructor category for %s", FAILING_VALIDATOR)
                        .contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
    }

    /**
     * The factory falls back to the no-argument constructor when the validator is not a bean,
     * which is why the hint has to make that constructor invokable.
     */
    @Test
    void everyValidatorHasTheNoArgumentConstructorTheFactoryLooksFor() {
        assertThat(ValidationRuntimeHints.validatorClassNames(getClass().getClassLoader()))
                .allSatisfy(name -> assertThat(constructorOf(name)).isNotNull());
    }

    private java.lang.reflect.Constructor<?> constructorOf(String className) {
        try {
            return Class.forName(className, false, getClass().getClassLoader())
                    .getDeclaredConstructor();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
