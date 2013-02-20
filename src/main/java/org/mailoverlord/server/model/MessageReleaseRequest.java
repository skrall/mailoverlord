package org.mailoverlord.server.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains the information required when releasing a messageIds from the database to the forwarding SMTP server.
 */
public class MessageReleaseRequest {

    private List<Long> messageIds = new ArrayList<>();
    private boolean overrideTo;
    private String overrideToAddresses;
    private boolean overrideFrom;
    private String overrideFromAddress;

    public List<Long> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<Long> messageIds) {
        this.messageIds = messageIds;
    }

    public void addMessageId(Long id) {
        messageIds.add(id);
    }

    public boolean isOverrideTo() {
        return overrideTo;
    }

    public void setOverrideTo(boolean overrideTo) {
        this.overrideTo = overrideTo;
    }

    public String getOverrideToAddresses() {
        return overrideToAddresses;
    }

    public void setOverrideToAddresses(String overrideToAddresses) {
        this.overrideToAddresses = overrideToAddresses;
    }

    public boolean isOverrideFrom() {
        return overrideFrom;
    }

    public void setOverrideFrom(boolean overrideFrom) {
        this.overrideFrom = overrideFrom;
    }

    public String getOverrideFromAddress() {
        return overrideFromAddress;
    }

    public void setOverrideFromAddress(String overrideFromAddress) {
        this.overrideFromAddress = overrideFromAddress;
    }
}
