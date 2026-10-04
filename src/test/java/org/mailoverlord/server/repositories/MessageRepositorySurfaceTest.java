package org.mailoverlord.server.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The production repository's surface, asserted rather than documented.
 */
class MessageRepositorySurfaceTest {

    /**
     * The prefixes Spring Data treats as a derived query, from {@code PropertyReference}. Anything
     * matching one gets an implementation built from its name, which is what makes it read as a
     * supported feature rather than as plumbing.
     */
    private static final Pattern DERIVED_QUERY = Pattern.compile("^(find|read|get|query|search)(First|Top|Distinct)?By.+");

    private List<String> declaredMethods() {
        return Arrays.stream(MessageRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
    }

    /**
     * {@code findByFrom} lived here once, called by tests and nothing else. It read like a query
     * the API offered, and it returned entities, so promoting it to a request path would have
     * quietly reloaded the DATA blob for every match. A query only a test wants belongs on
     * {@link TestMessageRepository} in test scope, where it can no longer be reached by accident.
     */
    @Test
    void declaresNoDerivedFinders() {
        assertThat(declaredMethods())
                .as("derived finders on the production repository; a test-only finder goes in TestMessageRepository")
                .noneMatch(name -> DERIVED_QUERY.matcher(name).matches());
    }

    /**
     * Hand-written methods are fine: {@code findSummaries} is one, and it projects rather than
     * hydrating entities, which is the property that matters on a request path.
     */
    @Test
    void keepsTheListingQuery() {
        assertThat(declaredMethods()).containsExactly("findSummaries");
    }
}