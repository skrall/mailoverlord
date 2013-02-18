package org.mailoverlord.server.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Request to delete messages.
 */
public class MessageDeleteRequest {

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
