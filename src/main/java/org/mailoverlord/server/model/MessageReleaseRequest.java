package org.mailoverlord.server.model;

import org.mailoverlord.server.entities.Message;

/**
 * Contains the information required when releasing a message from the database to the forwarding SMTP server.
 */
public class MessageReleaseRequest {

    private Message message;
    private boolean overrideTo;
    private String overrideToAddresses;
    private boolean overrideFrom;
    private String overrideFromAddress;

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
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
