package org.mailoverlord.server.config;

import java.util.List;

import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageViewData;
import org.mailoverlord.server.model.Pagination;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.data.domain.PageImpl;

/**
 * Runtime hints needed to render the web UI as a native image.
 *
 * <p>SpEL, which is what Thymeleaf evaluates {@code ${...}} expressions with, uses reflection
 * in two places, and a native image only keeps members it knows are reachable. Both need
 * registering, otherwise every request for the index page fails.
 */
public class WebUiRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * The {@code #numbers}, {@code #temporals} and friends that a template can call into.
     */
    private static final String EXPRESSION_OBJECTS_PATTERN = "classpath*:org/thymeleaf/expression/*.class";

    /**
     * Types a template reads properties from, which SpEL resolves through their getters.
     */
    private static final Class<?>[] VIEW_TYPES = {
            MessageViewData.class,
            Pagination.class,
            Message.class,
            PageImpl.class
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // SpEL reads properties reflectively, so without this the index page fails with
        // EL1008E: Property or field 'page' cannot be found on object of type 'MessageViewData'.
        for (Class<?> type : VIEW_TYPES) {
            hints.reflection().registerTypeIfPresent(classLoader, type.getName(), MemberCategory.INVOKE_PUBLIC_METHODS);
        }

        // SpEL also calls into the expression objects reflectively, so without this the
        // paginator fails with MissingReflectionRegistrationError on Numbers.sequence.
        List<String> expressionObjects = ClassPathScanner.findClassNames(classLoader, EXPRESSION_OBJECTS_PATTERN);
        for (String expressionObject : expressionObjects) {
            hints.reflection().registerTypeIfPresent(classLoader, expressionObject, MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }
}
