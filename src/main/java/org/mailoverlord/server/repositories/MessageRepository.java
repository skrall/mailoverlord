package org.mailoverlord.server.repositories;

import java.util.List;

import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByFrom(String from);

    /**
     * One page of the table, without the message bodies.
     *
     * <p>A plain {@code findAll(pageable)} selects every column, so each row's BLOB is loaded
     * into the persistence context and then thrown away again, since the table only ever shows
     * the size. At 25 rows against the 10 MB capture limit that is around a quarter of a gigabyte
     * of {@code byte[]} per request, on an endpoint the UI polls every ten seconds.
     *
     * <p>This selects only the columns the table actually displays. {@code SIZE_BYTES} exists
     * because the database cannot report the length of a BLOB through the portable subset of
     * SQL: {@code length} is a character count on some of the databases Mailoverlord supports
     * and a byte count on others, and Hibernate refuses {@code octet_length} on a BLOB outright.
     * The size is known exactly when the message is read off the socket, so it is recorded then.
     *
     * <p>The constructor expression keeps the projection honest: its arguments must line up with
     * {@link MessageSummary}, so adding or reordering a field fails to compile instead of
     * quietly putting a column in the wrong slot. {@code coalesce} covers rows captured before
     * the column existed, which have no recorded size.
     */
    @Query("select new org.mailoverlord.server.model.MessageSummary("
            + "m.id, m.from, m.to, m.receivedTimestamp, coalesce(m.sizeBytes, 0), m.subject) "
            + "from Message m")
    Page<MessageSummary> findSummaries(Pageable pageable);
}