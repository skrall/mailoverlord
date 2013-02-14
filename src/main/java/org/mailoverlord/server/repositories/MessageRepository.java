package org.mailoverlord.server.repositories;

import org.mailoverlord.server.entities.Message;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

/**
 * Message Repository
 */
public interface MessageRepository extends PagingAndSortingRepository<Message, Long> {

    List<Message> findByFrom(String from);

}
