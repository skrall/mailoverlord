package org.mailoverlord.server.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * The normalisation {@link MessageFilter} does before anything reaches the database.
 *
 * <p>These are plain unit tests on purpose: the behaviour worth pinning down here is what the
 * record does to its own arguments, and it does it in the constructor, so none of it needs a
 * database or a running application to observe.
 */
class MessageFilterTest {

    /**
     * An empty or whitespace-only search box means "no filter", not "match the empty string".
     *
     * <p>The columns are nullable, so a LIKE on an empty pattern would match every row that has
     * a subject and drop every row that does not. Clearing the box would then appear to delete
     * the no-subject messages from the table rather than reveal them.
     */
    @Test
    void blankTextIsTreatedAsAbsent() {
        MessageFilter filter = new MessageFilter("", "   ", "\t\n", null, null);

        assertThat(filter.subject()).isNull();
        assertThat(filter.from()).isNull();
        assertThat(filter.to()).isNull();
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        MessageFilter filter = new MessageFilter("  invoice  ", null, null, null, null);

        assertThat(filter.subject()).isEqualTo("invoice");
    }

    /**
     * Without escaping, searching for {@code %} returns the whole table, which reads as though
     * the filter was ignored, and {@code 50%} also matches {@code 50x}.
     */
    @Test
    void likeWildcardsAreEscaped() {
        MessageFilter filter = new MessageFilter("50%", "a_b", "!", null, null);

        assertThat(filter.subject()).isEqualTo("50!%");
        assertThat(filter.from()).isEqualTo("a!_b");
        assertThat(filter.to()).isEqualTo("!!");
    }

    /**
     * The escape character is doubled before the wildcards are prefixed with it. Doing it the
     * other way round would escape the prefix that was just added, and the search would match
     * nothing at all.
     */
    @Test
    void theEscapeCharacterIsEscapedFirst() {
        assertThat(new MessageFilter("!%", null, null, null, null).subject()).isEqualTo("!!!%");
    }

    /**
     * Only the text criteria are escaped. A timestamp is bound as a value rather than spliced
     * into a pattern, so escaping it would corrupt a legitimate date.
     */
    @Test
    void timestampsAreLeftAlone() {
        Instant when = Instant.parse("2024-03-01T12:00:00Z");

        assertThat(new MessageFilter(null, null, null, when, when).receivedFrom()).isEqualTo(when);
    }
}