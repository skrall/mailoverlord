package org.mailoverlord.server.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import org.hibernate.annotations.Nationalized;
import jakarta.persistence.PrePersist;
import java.time.Instant;

/**
 * Message entity.
 *
 * <p>The two character limits below are 2000 rather than something rounder because that is
 * the narrowest Unicode-capable text column any supported engine offers: Oracle's
 * {@code NVARCHAR2} tops out at 2000 <em>characters</em>. {@link Nationalized} makes the
 * declared length mean characters rather than bytes, so any column carrying it has to fit
 * inside that ceiling or the schema will not build on Oracle at all — which is a loud
 * failure, at least. 2000 characters remains far beyond a real header: RFC 5322 asks for
 * 78 and forbids a line over 998.
 */
@Entity
public class Message {

    /**
     * The widest address list kept on one row. See the class comment for why this is not
     * larger.
     */
    private static final int MAX_ADDRESS_LENGTH = 2000;

    /**
     * Matches the address length. A subject is a single header line, so this is generous,
     * but it is still a bound: the sender chooses it, so an unbounded subject would let one
     * message define the width of the row and defeat the column length Hibernate generates.
     */
    private static final int MAX_SUBJECT_LENGTH = 2000;

    private Long id;
    private String from;
    private String to = "";
    private String subject;
    private byte[] data;
    private long sizeBytes;
    private Instant receivedTimestamp;
    private Instant releasedTimestamp;

    @Id
    @GeneratedValue
    @Column(name = "MESSAGE_ID")
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Nationalized
    @Column(name = "FROM_ADDRESS")
    public String getFrom() {
        return from;
    }

    /**
     * Truncates if too long. The sender chooses the address, and a single absurdly long address
     * is not reason to drop the whole message: the schema would reject it anyway, so truncate
     * gracefully instead.
     */
    public void setFrom(String from) {
        if (from != null && from.length() > MAX_ADDRESS_LENGTH) {
            this.from = from.substring(0, MAX_ADDRESS_LENGTH);
        } else {
            this.from = from;
        }
    }

    @Nationalized
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
        String candidate = this.to + to;
        if (candidate.length() > MAX_ADDRESS_LENGTH) {
            if (this.to.length() >= MAX_ADDRESS_LENGTH) {
                return;
            }
            this.to = this.to.substring(0, MAX_ADDRESS_LENGTH - 1);
            if (!this.to.endsWith(",")) {
                this.to = this.to + ",";
            }
            return;
        }
        this.to = candidate;
    }

    /**
     * The subject, kept in its own column rather than read out of {@link #getData()} on
     * demand, so the database can order a page of messages by it.
     */
    @Nationalized
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

/**
 * The stored message, as received.
 *
 * <p>Every {@link String} column here is {@link Nationalized}, and that is load-bearing
 * rather than decorative. Left off, Hibernate creates {@code VARCHAR}, and on SQL Server
 * that is governed by the database collation — the stock {@code SQL_Latin1_General_CP1_CI_AS},
 * which stores U+0000 to U+00FF and nothing above. Latin-1 accents survive and everything
 * else does not: {@code Ünïcödé} round-trips while an emoji comes back as
 * {@code ??}, one question mark per surrogate, because the mangling happens per Java
 * {@code char} rather than per character.
 *
 * <p>That is silent and it is data loss. It hit subjects and, just as badly, addresses —
 * {@code DESTINATIONS} on the audit row holds mailboxes, and internationalized ones are
 * ordinary rather than exotic. The database matrix is what caught it: the same two tests
 * that had always passed on H2 failed only on the MSSQL leg.
 *
 * <p>{@code @Nationalized} asks each dialect for its Unicode-capable text type, so SQL
 * Server gets {@code NVARCHAR} while the others keep what they already had. Removing it
 * looks like a no-op on every engine except the one it breaks.
 *
 * <p>{@code @Lob} is what makes this a BLOB: without it Hibernate maps a {@code byte[]} to
 * {@code VARBINARY} and downgrades this to 255 bytes. The explicit {@code length} is what
 * keeps each dialect on its unbounded binary type. The default JPA column length of 255
 * leaks through to dialects that pick a concrete type by declared size: MySQL and MariaDB
 * then create a {@code TINYBLOB} and reject any real message with "Data too long for column
 * 'data'". {@code Integer.MAX_VALUE} exceeds every dialect's largest capacity, so they fall
 * back to their biggest blob type ({@code LONGBLOB}, {@code varbinary(max)}, H2 and Oracle
 * {@code BLOB}); Postgres ignores the declared length for a {@code @Lob}.
     *
     * <p>The size limit is therefore enforced while reading the message off the socket, in
     * {@code DatabaseMessageHandlerFactory}, and is configurable as
     * {@code mailoverlord.smtp.max-message-size}. The column itself is not the boundary.
     */
    @Lob
    @Column(name = "DATA", length = Integer.MAX_VALUE)
    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        this.sizeBytes = data == null ? 0 : data.length;
    }

    /**
     * The size of {@link #getData()} in bytes, recorded when the data is set.
     *
     * <p>This is derived rather than passed in, so it cannot drift from the blob. It exists
     * because the message table displays the size of every message it lists, and computing it
     * in SQL is not portable: {@code length} counts characters on some databases and bytes on
     * others, and Hibernate rejects {@code octet_length} on a BLOB. Reading the blob to measure
     * it instead would mean the list endpoint loads every message in full, which the table
     * exists specifically to avoid.
     *
     * <p>Rows written before this column existed have no value, which is why the summary query
     * coalesces it rather than trusting it to be present.
     */
    @Column(name = "SIZE_BYTES")
    public long getSizeBytes() {
        return sizeBytes;
    }

    /**
     * Used by Hibernate when it reads a row, and private so that nothing else can: the size is
     * derived in {@link #setData(byte[])}, and letting callers assign it separately is exactly
     * how the recorded value and the blob would drift apart.
     */
    private void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    @Column(name = "RECEIVED_TIMESTAMP")
    public Instant getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public void setReceivedTimestamp(Instant receivedTimestamp) {
        this.receivedTimestamp = receivedTimestamp;
    }

    @Column(name = "RELEASED_TIMESTAMP")
    public Instant getReleasedTimestamp() {
        return releasedTimestamp;
    }

    public void setReleasedTimestamp(Instant releasedTimestamp) {
        this.releasedTimestamp = releasedTimestamp;
    }

    @PrePersist
    private void prePersist() {
        this.receivedTimestamp = Instant.now();
    }
}
