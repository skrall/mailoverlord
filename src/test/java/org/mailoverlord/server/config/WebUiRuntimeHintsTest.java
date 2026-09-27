package org.mailoverlord.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;

/**
 * Tests for the web UI native image runtime hints.
 */
class WebUiRuntimeHintsTest {

    private RuntimeHints register() {
        RuntimeHints hints = new RuntimeHints();
        new WebUiRuntimeHints().registerHints(hints, getClass().getClassLoader());
        return hints;
    }

    private boolean hasMethodsFor(RuntimeHints hints, String className) {
        return hints.reflection().typeHints().toList().stream()
                .filter(hint -> hint.getType().getName().equals(className))
                .anyMatch(hint -> hint.getMemberCategories().contains(MemberCategory.INVOKE_PUBLIC_METHODS));
    }

    @Test
    void registersTheTypesTheTemplateReadsPropertiesFrom() {
        RuntimeHints hints = register();

        assertThat(hints.reflection().typeHints().map(hint -> hint.getType().getName()).toList())
                .as("types behind the ${...} expressions in index.html")
                .contains(
                        "org.mailoverlord.server.model.MessageViewData",
                        "org.mailoverlord.server.model.Pagination",
                        "org.mailoverlord.server.entities.Message",
                        "org.springframework.data.domain.PageImpl");
    }

    @Test
    void allowsSpelToInvokeGetters() {
        RuntimeHints hints = register();

        assertThat(hasMethodsFor(hints, "org.mailoverlord.server.model.MessageViewData"))
                .as("SpEL reads page through MessageViewData.getPage()")
                .isTrue();
    }

    @Test
    void registersTheExpressionObjectsTheTemplateCalls() {
        RuntimeHints hints = register();

        assertThat(hints.reflection().typeHints().map(hint -> hint.getType().getName()).toList())
                .as("the #numbers, #temporals and other Thymeleaf expression objects")
                .contains(
                        "org.thymeleaf.expression.Numbers",
                        "org.thymeleaf.expression.Temporals");
    }

    @Test
    void allowsInvokingExpressionObjectMethods() {
        RuntimeHints hints = register();

        assertThat(hasMethodsFor(hints, "org.thymeleaf.expression.Numbers"))
                .as("#numbers.sequence is called reflectively by SpEL")
                .isTrue();
    }
}
