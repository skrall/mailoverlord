package org.mailoverlord.server.service;

import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageDetail;
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

    PageResponse<MessageSummary> listMessages(Pageable pageable);

    /**
     * Loads a single message in full, or fails with a 404 when no message has that id.
     */
    MessageDetail getMessage(Long id);
}
