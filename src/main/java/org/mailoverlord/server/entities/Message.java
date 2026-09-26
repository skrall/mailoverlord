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
     * 10 MB, which comfortably covers the default SMTP size limit of most mail servers.
     */
    private static final int MAX_DATA_LENGTH = 10 * 1024 * 1024;

    private Long id;
    private String from;
    private String to = "";
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
