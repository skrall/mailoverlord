package org.mailoverlord.server.config;

import java.util.List;
import java.util.Objects;

import jakarta.validation.ConstraintValidator;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Runtime hints needed for Bean Validation to instantiate custom constraint validators in a
 * native image.
 *
 * <p>Hibernate Validator discovers a constraint's validator from the annotation and creates it
 * through its factory. Spring's factory asks the bean factory first and falls back to the
 * validator's no-argument constructor reflectively. That constructor is not registered by
 * Spring's AOT processing, so a native image fails the first time the constraint is validated:
 * <pre>
 * BeanCreationException: Error creating bean with name
 *     'org.mailoverlord.server.model.UsableOverridesValidator':
 *     No default constructor found
 * Caused by: java.lang.NoSuchMethodException:
 *     org.mailoverlord.server.model.UsableOverridesValidator.&lt;init&gt;()
 * </pre>
 *
 * <p>The built-in validators are unaffected: Hibernate Validator registers those itself. Only
 * the application's own validators are found by scanning for {@link ConstraintValidator}
 * implementations, so a new one does not silently reintroduce the failure.
 */
public class ValidationRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * The package the application's validators live under.
     */
    private static final String BASE_PACKAGE = "org.mailoverlord";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String validator : validatorClassNames(classLoader)) {
            hints.reflection().registerTypeIfPresent(classLoader, validator,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }

    /**
     * The binary names of every {@link ConstraintValidator} in the application, sorted, for
     * verification by the tests.
     */
    static List<String> validatorClassNames(ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.setResourceLoader(new PathMatchingResourcePatternResolver(classLoader));
        scanner.addIncludeFilter(new AssignableTypeFilter(ConstraintValidator.class));
        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map(definition -> definition.getBeanClassName())
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }
}
