package org.mailoverlord.server.entities;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

/**
 * Message entity.
 */
@Entity
public class Message {

    private Long id;
    private String from;
    private String to = "";
    private byte[] data;
    private Date receivedTimestamp;

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

    @Column(name = "TO")
    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void appendTo(String to) {
        if(this.to != null && this.to.length() > 0) {
            this.to += ",";
        }
        this.to += to;
    }

    @Column(name = "DATA")
    @Lob
    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Column(name = "RECEIVED_TIMESTAMP")
    @Temporal(TemporalType.TIMESTAMP)
    public Date getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public void setReceivedTimestamp(Date receivedTimestamp) {
        this.receivedTimestamp = receivedTimestamp;
    }

    @PrePersist
    private void prePersist() {
        this.receivedTimestamp = new Date();
    }
}
