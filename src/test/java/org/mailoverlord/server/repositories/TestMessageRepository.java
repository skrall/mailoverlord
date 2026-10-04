package org.mailoverlord.server.repositories;

import java.util.List;

import org.mailoverlord.server.entities.Message;
import org.springframework.data.repository.Repository;

/**
 * A finder that only tests need, declared in test scope so production does not advertise it.
 *
 * <p>This exists so a test can ask what the SMTP server actually captured from a given sender,
 * which is the only way to check that a handler wrote the row it was supposed to. It used to
 * live on {@link MessageRepository}, where it read like a supported query: a finder named
 * {@code findByFrom} in the production repository interface invites someone to build a filter on
 * it, and the production interface has exactly one finder on purpose.
 *
 * <p>It extends the bare {@link Repository} marker rather than {@code JpaRepository} so that the
 * test-only surface is the single finder and nothing else. The tests that need to store or clear
 * messages use the production repository, which is where those methods genuinely belong.
 *
 * <p>It returns entities, which means every match hydrates its {@code DATA} blob. That is
 * harmless in a test holding a handful of small messages and is the reason this must not become a
 * request-path query: at 25 rows against the 10 MB capture limit it is around a quarter of a
 * gigabyte of {@code byte[]} per page. The listing endpoint uses {@code MessageSummary} instead,
 * for the same reason.
 *
 * <p>Deliberately narrow: extending {@code JpaRepository} instead would hand this the save and
 * delete methods too, which is production surface wearing a test-only name.
 */
public interface TestMessageRepository extends Repository<Message, Long> {

    List<Message> findByFrom(String from);
}