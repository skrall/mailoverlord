package org.mailoverlord.server.model;

import org.mailoverlord.server.entities.Message;
import org.springframework.data.domain.Page;

/**
 * Wraps a page of messages so the view template can be type safe.
 */
public class MessageViewData {

    private final Page<Message> page;

    public MessageViewData(Page<Message> page) {
        this.page = page;
    }

    public Page<Message> getPage() {
        return page;
    }
}
