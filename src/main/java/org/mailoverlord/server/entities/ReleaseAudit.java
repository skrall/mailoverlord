package org.mailoverlord.server.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;

import org.mailoverlord.server.model.ReleaseOutcome;

/**
 * One row per release attempt, recording who asked for what to go where.
 *
 * <p>Release forwards captured mail out to real recipients, and the only record of it used to be
 * the released timestamp on each message. This is the trail behind that timestamp: which
 * destinations were resolved for the batch and what became of it, so a later question of "what
 * went to these people and when" can be answered without the messages still being in the store.
 */
@Entity
public class ReleaseAudit {

    private Long id;
    private Instant releasedAt;
    private String source;
    private String messageIds;
    private String overrideTo;
    private String destinations;
    private ReleaseOutcome outcome;

    @Id
    @GeneratedValue
    @Column(name = "RELEASE_AUDIT_ID")
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Column(name = "RELEASED_AT")
    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    /**
     * The remote address for now, becoming the authenticated principal once authentication
     * exists. Nullable on purpose so that path does not require a schema change; see #2.
     */
    @Column(name = "SOURCE")
    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    /**
     * The requested ids as a JSON array, e.g. {@code "[1, 2]"}, one byte of JSON in a text
     * column rather than a relation, since the ids are a flat list that is read for display
     * only, never queried.
     */
    @Lob
    @Column(name = "MESSAGE_IDS")
    public String getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(String messageIds) {
        this.messageIds = messageIds;
    }

    /**
     * The substitution recipients when the release overrode them, or {@code null} when it sent
     * to the stored recipients.
     */
    @Column(name = "OVERRIDE_TO")
    public String getOverrideTo() {
        return overrideTo;
    }

    public void setOverrideTo(String overrideTo) {
        this.overrideTo = overrideTo;
    }

    /**
     * The resolved recipients of the batch, comma-separated.
     */
    @Lob
    @Column(name = "DESTINATIONS")
    public String getDestinations() {
        return destinations;
    }

    public void setDestinations(String destinations) {
        this.destinations = destinations;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "OUTCOME")
    public ReleaseOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(ReleaseOutcome outcome) {
        this.outcome = outcome;
    }
}