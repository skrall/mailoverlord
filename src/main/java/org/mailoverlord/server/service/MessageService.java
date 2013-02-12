package org.mailoverlord.server.service;

import org.mailoverlord.server.model.MessageReleaseRequest;

/**
 * Message Service
 */
public interface MessageService {

    public void releaseMessage(MessageReleaseRequest request);

}
