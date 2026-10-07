package org.mailoverlord.server.service;

import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageDetail;
import org.mailoverlord.server.model.MessageFilter;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.model.MessageReleaseResponse;
import org.mailoverlord.server.model.MessageSummary;
import org.mailoverlord.server.model.PageResponse;
import org.springframework.data.domain.Pageable;

/**
 * Message Service
 */
public interface MessageService {

    /**
     * Delivers each requested message, reporting an outcome per id.
     *
     * <p>A failure partway through does not abandon the rest of the batch, and does not throw.
     * Delivery is not reversible, so a caller told only that "the release failed" would have no
     * way to know which messages already reached an inbox.
     *
     * <p>When {@code mailoverlord.release.allowed-destinations} is configured, a recipient the
     * list refuses is not released: an override address asked for up front is rejected before
     * anything is attempted, and a recipient the stored message already carries fails that one
     * id. Either way the rejected address is named in the outcome.
     *
     * <p>Every call is recorded in the release audit trail.
     *
     * @param source who asked for the release, recorded in the audit trail
     */
    MessageReleaseResponse releaseMessage(MessageReleaseRequest request, String source);

    void deleteMessage(MessageDeleteRequest request);

    /**
     * One page of message summaries, narrowed by {@code filter}.
     *
     * <p>The filter is applied by the database rather than to the returned page, so the total
     * covers every match and not just the ones that happened to fall on this page. Filtering
     * client-side would hide matches on later pages while looking like a complete answer.
     */
    PageResponse<MessageSummary> listMessages(Pageable pageable, MessageFilter filter);

    /**
     * Loads a single message in full, or fails with a 404 when no message has that id.
     */
    MessageDetail getMessage(Long id);
}
