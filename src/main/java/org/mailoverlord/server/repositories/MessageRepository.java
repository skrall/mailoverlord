package org.mailoverlord.server.repositories;

import java.util.List;

import org.mailoverlord.server.entities.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 * Message Repository
 */
@RepositoryRestResource(collectionResourceRel = "message", path = "message")
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByFrom(String from);
}
