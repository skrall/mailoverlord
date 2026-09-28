package org.mailoverlord.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;

/**
 * Tests for the JSON API native image runtime hints.
 */
class ApiModelRuntimeHintsTest {

    private RuntimeHints register() {
        RuntimeHints hints = new RuntimeHints();
        new ApiModelRuntimeHints().registerHints(hints, getClass().getClassLoader());
        return hints;
    }

    private boolean hasCategoryFor(RuntimeHints hints, String className, MemberCategory category) {
        return hints.reflection().typeHints().toList().stream()
                .filter(hint -> hint.getType().getName().equals(className))
                .anyMatch(hint -> hint.getMemberCategories().contains(category));
    }

    @Test
    void registersEveryTypeTheApiReturns() {
        RuntimeHints hints = register();

        assertThat(hints.reflection().typeHints().map(hint -> hint.getType().getName()).toList())
                .as("types Jackson serialises for the JSON API")
                .contains(
                        "org.mailoverlord.server.model.MessageSummary",
                        "org.mailoverlord.server.model.MessageDetail",
                        "org.mailoverlord.server.model.PageResponse",
                        "org.mailoverlord.server.model.MessageResponse");
    }

    /**
     * The response types are records, so Jackson needs the canonical constructor to build
     * them as well as the accessors to read them.
     */
    @Test
    void allowsJacksonToReadRecordAccessors() {
        RuntimeHints hints = register();

        assertThat(hasCategoryFor(hints, "org.mailoverlord.server.model.MessageSummary",
                MemberCategory.INVOKE_PUBLIC_METHODS)).isTrue();
    }

    @Test
    void allowsJacksonToBuildRecordInstances() {
        RuntimeHints hints = register();

        assertThat(hasCategoryFor(hints, "org.mailoverlord.server.model.PageResponse",
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)).isTrue();
    }
}
