package org.mailoverlord.server.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mailoverlord.server.AbstractMailoverlordIntegrationTest;
import org.mailoverlord.server.entities.Message;
import org.springframework.http.MediaType;

/**
 * Filtering the message table, which is issue #23.
 *
 * <p>Every test here goes through the HTTP layer rather than calling the service, because the
 * parts most likely to break are the ones in between: the query parameters have to bind, and the
 * total the response reports has to agree with the rows it returned.
 */
class MessageSearchControllerTest extends AbstractMailoverlordIntegrationTest {

    @Test
    void subjectMatchesOnASubstring() throws Exception {
        saveMessage("a@test.com", "one@test.com", "Invoice for March");
        saveMessage("b@test.com", "two@test.com", "Newsletter");

        mockMvc.perform(get("/messages/list").param("subject", "invoice")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Invoice for March"));
    }

    /**
     * A person looking for mail does not remember which case the sender used, and the column is
     * denormalised text rather than something the database compares case-insensitively for free.
     * The total has to be filtered the same way as the page, or the two disagree.
     */
    @Test
    void textMatchingIgnoresCaseInBothTheRowsAndTheTotal() throws Exception {
        saveMessage("a@test.com", "one@test.com", "INVOICE");
        saveMessage("b@test.com", "two@test.com", "Newsletter");

        mockMvc.perform(get("/messages/list").param("subject", "invoice")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("INVOICE"));
    }

    @Test
    void fromAndToAreBothSearchable() throws Exception {
        saveMessage("alice@example.com", "bob@example.com", "One");
        saveMessage("carol@example.com", "dave@example.com", "Two");

        mockMvc.perform(get("/messages/list").param("from", "ALICE").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].from").value("alice@example.com"));

        mockMvc.perform(get("/messages/list").param("to", "dave").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].to").value("dave@example.com"));
    }

    /**
     * A recipient list is stored comma-separated in one column, so searching one address has to
     * find a message addressed to several.
     *
     * <p>The address list is built through {@code appendTo}, which is how the SMTP handler
     * accumulates it, rather than by handing the helper one comma-joined string that
     * {@code InternetAddress} would reject.
     */
    @Test
    void toMatchesAnyAddressInTheList() throws Exception {
        saveMessageToMany("a@test.com", List.of("first@test.com", "second@test.com"), "One");
        saveMessageToMany("b@test.com", List.of("third@test.com"), "Two");

        mockMvc.perform(get("/messages/list").param("to", "second").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/messages/list").param("to", "third").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void criteriaAreCombinedWithAnd() throws Exception {
        saveMessage("alice@example.com", "bob@example.com", "Invoice");
        saveMessage("alice@example.com", "bob@example.com", "Newsletter");
        saveMessage("carol@example.com", "bob@example.com", "Invoice");

        mockMvc.perform(get("/messages/list").param("subject", "invoice").param("from", "alice")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].from").value("alice@example.com"));
    }

    /**
     * The reason filtering has to happen in the database. A page of one holding two of the three
     * matches reports a total of two, so the client knows there is another page to ask for. A
     * client-side filter would have hidden the third match and claimed there were none.
     */
    @Test
    void theTotalCountsEveryMatchNotJustThisPage() throws Exception {
        saveMessage("a@test.com", "one@test.com", "Invoice one");
        saveMessage("b@test.com", "two@test.com", "Invoice two");
        saveMessage("c@test.com", "three@test.com", "Newsletter");

        mockMvc.perform(get("/messages/list").param("subject", "invoice").param("size", "1")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));
    }

    /**
     * Clearing the filter has to bring the no-subject messages back, which is exactly what breaks
     * if a blank parameter is treated as a search for the empty string: the pattern would then
     * match every row that has a subject and none that does not.
     */
    @Test
    void aBlankFilterHidesNothing() throws Exception {
        saveMessageWithoutSubject("a@test.com", "one@test.com");
        saveMessage("b@test.com", "two@test.com", "Has a subject");

        mockMvc.perform(get("/messages/list").param("subject", "").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/messages/list").param("subject", "   ")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    /**
     * A search term containing a LIKE wildcard is a literal. If it were not escaped, searching
     * for a percent sign would return the whole table and read as a filter that was ignored.
     */
    @Test
    void wildcardsInTheSearchTermAreLiteral() throws Exception {
        saveMessage("a@test.com", "one@test.com", "50% off");
        saveMessage("b@test.com", "two@test.com", "50x off");
        saveMessage("c@test.com", "three@test.com", "Nothing to see");

        mockMvc.perform(get("/messages/list").param("subject", "50%")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("50% off"));
    }

    @Test
    void underscoresInTheSearchTermAreLiteral() throws Exception {
        saveMessage("a@test.com", "one@test.com", "order_42");
        saveMessage("b@test.com", "two@test.com", "orderX42");

        mockMvc.perform(get("/messages/list").param("subject", "order_")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("order_42"));
    }

    @Test
    void aReceivedRangeExcludesMailOutsideIt() throws Exception {
        saveMessageAt("a@test.com", "one@test.com", "Too early",
                Instant.parse("2024-01-01T00:00:00Z"));
        saveMessageAt("b@test.com", "two@test.com", "Inside",
                Instant.parse("2024-03-01T12:00:00Z"));
        saveMessageAt("c@test.com", "three@test.com", "Too late",
                Instant.parse("2024-06-01T00:00:00Z"));

        mockMvc.perform(get("/messages/list")
                .param("receivedFrom", "2024-02-01T00:00:00Z")
                .param("receivedTo", "2024-04-01T00:00:00Z")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Inside"));
    }

    @Test
    void aReceivedRangeWithOnlyOneBoundIsOpenEnded() throws Exception {
        saveMessageAt("a@test.com", "one@test.com", "Old",
                Instant.parse("2024-01-01T00:00:00Z"));
        saveMessageAt("b@test.com", "two@test.com", "New",
                Instant.parse("2024-03-01T12:00:00Z"));

        mockMvc.perform(get("/messages/list").param("receivedFrom", "2024-02-01T00:00:00Z")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("New"));

        mockMvc.perform(get("/messages/list").param("receivedTo", "2024-02-01T00:00:00Z")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Old"));
    }

    /**
     * Both ends are inclusive, so a range that starts and ends at the same instant still finds
     * a message that arrived exactly then. Otherwise the UI would have to add a unit to the
     * user's chosen bound to avoid silently dropping the message they were looking at.
     */
    @Test
    void receivedRangeBoundsAreInclusive() throws Exception {
        saveMessageAt("a@test.com", "one@test.com", "Exactly then",
                Instant.parse("2024-03-01T12:00:00Z"));

        mockMvc.perform(get("/messages/list")
                .param("receivedFrom", "2024-03-01T12:00:00Z")
                .param("receivedTo", "2024-03-01T12:00:00Z")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /**
     * A timestamp the server cannot parse is a bad request, not a filter that matched nothing.
     * Answering 200 with an empty list would tell the user their mail is gone.
     */
    @Test
    void anUnparseableTimestampIsRejected() throws Exception {
        saveMessage("a@test.com", "one@test.com", "One");

        mockMvc.perform(get("/messages/list").param("receivedFrom", "yesterday")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aFilterThatMatchesNothingIsAnEmptyPageRatherThanAnError() throws Exception {
        saveMessage("a@test.com", "one@test.com", "Invoice");

        mockMvc.perform(get("/messages/list").param("subject", "no such thing")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    /**
     * The filter and the sort have to compose. Sorting first and filtering after would filter a
     * page that had already been cut, which is the same mistake as filtering client-side.
     */
    @Test
    void aFilteredListIsStillSorted() throws Exception {
        saveMessage("a@test.com", "one@test.com", "Invoice alpha");
        saveMessage("b@test.com", "two@test.com", "Invoice beta");
        saveMessage("c@test.com", "three@test.com", "Newsletter");

        mockMvc.perform(get("/messages/list").param("subject", "invoice")
                .param("sort", "subject,asc").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subject").value("Invoice alpha"))
                .andExpect(jsonPath("$.content[1].subject").value("Invoice beta"));
    }

    /**
     * Paging still works inside a filter, otherwise a filtered list over more messages than fit
     * on one page would be unreachable past the first page.
     */
    @Test
    void pagingRespectsTheFilter() throws Exception {
        saveMessage("a@test.com", "one@test.com", "Invoice one");
        saveMessage("b@test.com", "two@test.com", "Invoice two");
        saveMessage("c@test.com", "three@test.com", "Newsletter");

        mockMvc.perform(get("/messages/list").param("subject", "invoice").param("size", "1")
                .param("page", "1").param("sort", "subject,asc")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Invoice two"))
                .andExpect(jsonPath("$.last").value(true));
    }

    /**
     * Filtering must not drag the message bodies back in. A plain {@code findAll(pageable)}
     * selects every column, which is the mistake issue #8 was about; a filter that reached for
     * the entities to inspect them in Java would reintroduce it.
     */
    @Test
    void aFilteredListOmitsTheMessageBody() throws Exception {
        saveMessage("a@test.com", "one@test.com", "Invoice");

        mockMvc.perform(get("/messages/list").param("subject", "invoice")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].data").doesNotExist())
                .andExpect(jsonPath("$.content[0].sizeBytes").isNumber());
    }

    /**
     * With nothing filtered the endpoint has to behave exactly as it did before search existed,
     * since that is the state the UI is in when the filter box is empty.
     */
    @Test
    void noFilterReturnsEverythingSortedNewestFirst() throws Exception {
        Message older = saveMessage("older@test.com", "to@test.com", "Older");
        Message newer = saveMessage("newer@test.com", "to@test.com", "Newer");
        setReceived(older, Instant.parse("2020-01-01T00:00:00Z"));
        setReceived(newer, Instant.parse("2024-01-01T00:00:00Z"));

        mockMvc.perform(get("/messages/list").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].from").value("newer@test.com"))
                .andExpect(jsonPath("$.content[1].from").value("older@test.com"));
    }

    /**
     * A filter must not become a way to read past the page cap. The cap is a bound on how much
     * one request can cost, and a caller supplying its own filter does not get to raise it.
     */
    @Test
    void filteringDoesNotBypassThePageSizeCap() throws Exception {
        mockMvc.perform(get("/messages/list").param("subject", "invoice").param("size", "5000")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private void setReceived(Message message, Instant when) {
        Message stored = messageRepository.findById(message.getId()).orElseThrow();
        stored.setReceivedTimestamp(when);
        messageRepository.save(stored);
    }

    private void saveMessageAt(String from, String to, String subject, Instant when) throws Exception {
        setReceived(saveMessage(from, to, subject), when);
    }

    /**
     * Builds the comma-separated recipient column the way the SMTP handler does, one recipient
     * at a time.
     */
    private void saveMessageToMany(String from, List<String> recipients, String subject)
            throws Exception {
        Message stored = messageRepository.findById(
                saveMessage(from, recipients.get(0), subject).getId()).orElseThrow();
        for (String recipient : recipients.subList(1, recipients.size())) {
            stored.appendTo(recipient);
        }
        messageRepository.save(stored);
    }
}