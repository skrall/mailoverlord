package org.mailoverlord.server.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains the information required when releasing a messageIds from the database to the forwarding SMTP server.
 *
 * <p>The constraints here are the whole of the request validation: {@code messageIds} has to name
 * something and cannot name an unbounded number of things, and {@link UsableOverrides} covers the
 * substitution flags. That is deliberate. These were once guards in the controller, applied to
 * some fields and missed on others, which is how asking to override the recipients without
 * naming any arrived at the service and threw. See #18.
 */
@UsableOverrides
public class MessageReleaseRequest {

    @NotEmpty(message = "messageIds must contain at least one message id.")
    @Size(max = MessageDeleteRequest.MAX_IDS_PER_REQUEST,
            message = "messageIds must contain at most {max} message ids.")
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
