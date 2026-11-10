package org.mailoverlord.server.config;

import java.util.List;
import java.util.Objects;

import jakarta.persistence.Entity;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Runtime hints needed for Hibernate to load the JPA entities in a native image.
 *
 * <p>Hibernate reads a row by calling each property's setter reflectively. Spring Boot's AOT
 * processing registers the public accessors it finds on an entity, but a non-public accessor is
 * left out of the metadata, and a native image refuses to invoke it:
 * <pre>
 * MissingReflectionRegistrationError: Cannot reflectively invoke method
 *     'private void org.mailoverlord.server.entities.Message.setSizeBytes(long)'
 * </pre>
 *
 * <p>{@link org.mailoverlord.server.entities.Message#getSizeBytes()} is the case that surfaced
 * this. Its setter is private on purpose — the size is derived from the blob so that the two
 * cannot drift apart — but Hibernate still has to call it when it materialises a row, so the
 * category registered here is the declared one rather than the public one.
 *
 * <p>The entities are found by scanning for {@code @Entity} rather than listed by name, so a new
 * entity, or a new non-public accessor on an existing one, does not silently reintroduce the
 * failure.
 */
public class JpaEntityRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * The package the application's entities live under.
     */
    private static final String BASE_PACKAGE = "org.mailoverlord";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String entity : entityClassNames(classLoader)) {
            hints.reflection().registerTypeIfPresent(classLoader, entity,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);
        }
    }

    /**
     * The binary names of every {@code @Entity} in the application, sorted, for verification by
     * the tests.
     */
    static List<String> entityClassNames(ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.setResourceLoader(new PathMatchingResourcePatternResolver(classLoader));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map(definition -> definition.getBeanClassName())
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }
}
