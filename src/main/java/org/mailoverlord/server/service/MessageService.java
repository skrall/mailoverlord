package org.mailoverlord.server.service;

import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageDetail;
import org.mailoverlord.server.model.MessageFilter;
import org.mailoverlord.server.model.MessageReleaseRequest;
import org.mailoverlord.server.model.MessageSummary;
import org.mailoverlord.server.model.PageResponse;
import org.springframework.data.domain.Pageable;

/**
 * Message Service
 */
public interface MessageService {

    void releaseMessage(MessageReleaseRequest request);
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
