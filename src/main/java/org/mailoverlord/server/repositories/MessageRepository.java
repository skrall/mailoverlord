package org.mailoverlord.server.repositories;

import org.mailoverlord.server.entities.Message;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

/**
 * Message Repository
 */
@RepositoryRestResource(collectionResourceRel = "message", path = "message")
public interface MessageRepository extends PagingAndSortingRepository<Message, Long> {

    List<Message> findByFrom(@Param("from") String from);

}
