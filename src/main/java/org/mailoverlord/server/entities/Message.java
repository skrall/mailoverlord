package org.mailoverlord.server.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import java.time.Instant;

/**
 * Message entity.
 */
@Entity
public class Message {

    private static final int MAX_ADDRESS_LENGTH = 4000;

    /**
     * Matches the address length. A subject is a single header line, so this is generous,
     * but it is still a bound: the sender chooses it, so an unbounded subject would let one
     * message define the width of the row and defeat the column length Hibernate generates.
     */
    private static final int MAX_SUBJECT_LENGTH = 4000;

    /**
     * 10 MB, which comfortably covers the default SMTP size limit of most mail servers.
     */
    private static final int MAX_DATA_LENGTH = 10 * 1024 * 1024;

    private Long id;
    private String from;
    private String to = "";
    private String subject;
    private byte[] data;
    private Instant receivedTimestamp;

    @Id
    @GeneratedValue
    @Column(name = "MESSAGE_ID")
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Column(name = "FROM_ADDRESS")
    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    @Column(name = "TO_ADDRESSES", length = MAX_ADDRESS_LENGTH)
    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void appendTo(String to) {
        if (this.to != null && !this.to.isEmpty()) {
            this.to += ",";
        }
        this.to += to;
    }

    /**
     * The subject, kept in its own column rather than read out of {@link #getData()} on
     * demand, so the database can order a page of messages by it.
     */
    @Column(name = "SUBJECT", length = MAX_SUBJECT_LENGTH)
    public String getSubject() {
        return subject;
    }

    /**
     * Truncates rather than rejecting. The sender chooses this value, and a message whose
     * subject is absurdly long is still a message worth capturing; losing the tail of its
     * subject is a better outcome than refusing it, and refusing it would put the column
     * length at the mercy of whatever a client sends.
     */
    public void setSubject(String subject) {
        if (subject != null && subject.length() > MAX_SUBJECT_LENGTH) {
            this.subject = subject.substring(0, MAX_SUBJECT_LENGTH);
        } else {
            this.subject = subject;
        }
    }

    @Column(name = "DATA", length = MAX_DATA_LENGTH)
    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Column(name = "RECEIVED_TIMESTAMP")
    public Instant getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public void setReceivedTimestamp(Instant receivedTimestamp) {
        this.receivedTimestamp = receivedTimestamp;
    }

    @PrePersist
    private void prePersist() {
        this.receivedTimestamp = Instant.now();
    }
}
