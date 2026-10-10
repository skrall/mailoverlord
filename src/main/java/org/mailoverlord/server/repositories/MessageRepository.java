package org.mailoverlord.server.repositories;

import java.time.Instant;
import java.util.List;

import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.model.MessageFilter;
import org.mailoverlord.server.model.MessageSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reading captured mail, for the application.
 *
 * <p>Derived finders are absent on purpose. The one this interface used to carry,
 * {@code findByFrom}, was called only by tests, and a finder sitting here reads like a supported
 * query; it now lives in {@code TestMessageRepository} in test scope. What the table needs is
 * {@link #findSummaries}, which projects rather than hydrates entities.
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * One page of the table, without the message bodies, narrowed by the given criteria.
     *
     * <p>A plain {@code findAll(pageable)} selects every column, so each row's BLOB is loaded
     * into the persistence context and then thrown away again, since the table only ever shows
     * the size. At 25 rows against the 10 MB capture limit that is around a quarter of a gigabyte
     * of {@code byte[]} per request, on an endpoint the UI polls every ten seconds. Filtering
     * must not reintroduce that, so this is the one query the table uses, filtered or not: there
     * is deliberately no separate full-entity finder to fall back on.
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
     *
     * <p>Filtering lives in the query rather than in a {@code Specification} because
     * {@code JpaSpecificationExecutor} returns entities, which would drag every matching BLOB
     * into memory to satisfy a page of rows that never displays one. It is also what keeps the
     * total honest: Spring Data derives the count query from this same string, so the page and
     * the count are guaranteed to agree about case handling and escaping. Two hand-written
     * queries could drift, and a full page reporting a total that disagrees with it is exactly
     * the bug that makes a filter look broken.
     *
     * <p>Each criterion is guarded by {@code :param is null}, so an absent filter matches
     * everything and no query has to be built dynamically. The cost is that the database cannot
     * use an index on the text columns, which is a fair trade here: there are no indexes on them
     * to begin with, and this is a tool for a test environment rather than a mail archive.
     *
     * <p>Text matching is case-insensitive via {@code lower} on both sides rather than a
     * case-insensitive collation, because the collation is a database setting and the case
     * behaviour has to be identical everywhere. The {@code escape} clause matches the escaping
     * {@link MessageFilter} applies; see that class for why the wildcards are not left alone.
     *
     * <p>The {@code cast(... as ...)} around each parameter gives PostgreSQL a type to bind.
     * Hibernate binds a null {@code String} as an untyped parameter, and PostgreSQL cannot infer
     * a type for a parameter that only appears under {@code is null} (the comparison operands do
     * not help: each usage is a separate placeholder). Left untyped, {@code lower(?) } is read as
     * {@code lower(bytea)}, which does not exist, so the query aborts at runtime:
     * {@code 42P18}/{@code 42883}. The {@code is null} guard keeps an empty filter matching
     * everything, the cast's value is only null-tested there, and the comparison operand on the
     * right keeps its original typed binding, so no filtering behaviour changes.
     *
     * <p>The parameter names are deliberately not {@code from} and {@code to}: those are JPQL
     * keywords, and a named parameter that collides with one is not worth the ambiguity.
     */
    @Query("select new org.mailoverlord.server.model.MessageSummary("
            + "m.id, m.from, m.to, m.receivedTimestamp, m.releasedTimestamp, coalesce(m.sizeBytes, 0), m.subject) "
            + "from Message m "
            + "where (cast(:subjectText as string) is null "
            + "    or lower(m.subject) like concat('%', lower(cast(:subjectText as string)), '%') escape '!') "
            + "and (cast(:fromText as string) is null "
            + "    or lower(m.from) like concat('%', lower(cast(:fromText as string)), '%') escape '!') "
            + "and (cast(:toText as string) is null "
            + "    or lower(m.to) like concat('%', lower(cast(:toText as string)), '%') escape '!') "
            + "and (cast(:receivedFrom as timestamp) is null or m.receivedTimestamp >= :receivedFrom) "
            + "and (cast(:receivedTo as timestamp) is null or m.receivedTimestamp <= :receivedTo)")
    Page<MessageSummary> findSummaries(@Param("subjectText") String subjectText,
            @Param("fromText") String fromText,
            @Param("toText") String toText,
            @Param("receivedFrom") Instant receivedFrom,
            @Param("receivedTo") Instant receivedTo,
            Pageable pageable);

    /**
     * The ids of the newest messages, newest first.
     *
     * <p>This is the read half of retention, and it selects ids alone: the rows being discarded
     * are exactly the ones whose bodies nobody will look at, so loading each of their BLOBs to
     * throw them away would defeat the point of bounding memory. The delete that follows works
     * from these ids for the same reason {@link #findSummaries} projects.
     *
     * <p>Newest first is what makes a cap a slice rather than an offset. The caller drops the
     * head of the list to spare the newest {@code keep}, and everything past it is what falls off
     * the end. Returning oldest-first would invert that silently: dropping the head would spare
     * the oldest mail and evict the newest, which for a tool whose job is "keep this specific
     * email" is the worst possible direction to get it backwards in.
     *
     * <p>That is also why the cap is not expressed as a {@code Pageable} offset. {@code Pageable}
     * spells an offset only as {@code page * size}, so there is no way to say "skip exactly 3"
     * without multiplying, and {@code PageRequest.of(3, 500)} asks to skip 1500 rows — which
     * discards the newest mail the moment the cap exceeds one.
     *
     * <p>The order is {@code receivedTimestamp} then {@code id} so that messages arriving in the
     * same millisecond keep a fixed order between runs. Without the id, which rows land past the
     * head is not deterministic, and eviction could drop a different message on every sweep for
     * the same stored data. The same two-key ordering is what the table's sort allowlist falls
     * back to, for the same reason.
     *
     * @param pageable the page to read; the sweep asks for the newest, capped by its batch size
     */
    @Query("select m.id from Message m order by m.receivedTimestamp desc, m.id desc")
    List<Long> findIdsNewestFirst(Pageable pageable);

    /**
     * How many messages are stored, for the sweep's log line.
     *
     * <p>A {@code count()} over the whole table rather than the size of the page the query above
     * returned: whether anything was dropped is worth stating exactly, and a count reads a
     * single row rather than hydrating anything.
     */
    long count();
}