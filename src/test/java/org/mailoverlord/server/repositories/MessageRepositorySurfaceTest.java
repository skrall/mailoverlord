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
     * Hand-written methods are fine, and this is the closed list of them:
     * {@code findSummaries} is the request-path listing query and projects rather than hydrating
     * entities; {@code findIdsNewestFirst} and {@code count} back the retention sweep and select
     * ids, so discarding the overflow never loads the blobs it is throwing away.
     *
     * <p>The set is exact rather than a minimum. Every method named here is one someone reasoned
     * about, and a new one arriving by default would carry no reasoning at all — which is the
     * failure {@code findByFrom} was, in a form that a {@code contains} would have let through.
     * Adding a query means adding it here and saying why it belongs.
     */
    @Test
    void keepsTheListingQuery() {
        assertThat(declaredMethods()).containsExactlyInAnyOrder(
                "findSummaries",
                "findIdsNewestFirst",
                "count");
    }

    /**
     * The sweep's own query must not hydrate messages. It selects ids alone, and the rows it
     * returns are the ones about to be deleted — the bodies would be loaded from disk into the
     * heap purely to be discarded, which is the opposite of what a retention cap is for.
     */
    @Test
    void retentionReadsIdsRatherThanMessages() {
        Method retentionQuery = Arrays.stream(MessageRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("findIdsNewestFirst"))
                .findFirst()
                .orElseThrow();

        assertThat(retentionQuery.getReturnType())
                .as("the retention query must return ids, not entities")
                .isEqualTo(List.class);
    }
}