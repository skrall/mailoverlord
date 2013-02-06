package org.mailoverlord.server.repositories;

import org.mailoverlord.server.entities.Message;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Message Repository
 */
public interface MessageRepository extends PagingAndSortingRepository<Message, Long> {

}
