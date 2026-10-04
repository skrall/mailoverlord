package org.mailoverlord.server.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * Request to delete messages.
 */
public class MessageDeleteRequest {

    /**
     * The most ids one request may name.
     *
     * <p>The service looks the batch up with a single {@code findAllById}, which becomes one
     * {@code IN} list, so the size of the request is the size of the SQL statement. Nothing
     * bounded that: a request naming hundreds of thousands of ids built a statement to match.
     *
     * <p>The number is Spring Data's default maximum page size, which is also the most the table
     * can offer: selection is scoped to the page on screen, so the UI cannot express a larger
     * batch. Anything above this is therefore a hand-written request rather than a UI action, and
     * both supported databases plan a list this size without complaint.
     */
    public static final int MAX_IDS_PER_REQUEST = 2000;

    @NotEmpty(message = "messageIds must contain at least one message id.")
    @Size(max = MAX_IDS_PER_REQUEST,
            message = "messageIds must contain at most {max} message ids.")
    private List<Long> messageIds = new ArrayList<>();

    public List<Long> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<Long> messageIds) {
        this.messageIds = messageIds;
    }

    public void addMessageId(Long id) {
        messageIds.add(id);
    }
}