package org.mailoverlord.server.model;

import org.mailoverlord.server.entities.Message;
import org.springframework.data.domain.Page;

/**
 * Just a wrapper so JSTL can be type safe.
 */
public class MessageJspData {

    private Page<Message> page;

    public MessageJspData(Page<Message> page) {
        this.page = page;
    }

    public Page<Message> getPage() {
        return page;
    }

    public void setPage(Page<Message> page) {
        this.page = page;
    }
}
