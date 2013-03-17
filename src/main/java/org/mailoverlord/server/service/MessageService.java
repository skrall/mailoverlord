package org.mailoverlord.server.service;

import org.mailoverlord.server.model.MessageDeleteRequest;
import org.mailoverlord.server.model.MessageReleaseRequest;

/**
 * Message Service
 */
public interface MessageService {

    void releaseMessage(MessageReleaseRequest request);
    void deleteMessage(MessageDeleteRequest request);

}
