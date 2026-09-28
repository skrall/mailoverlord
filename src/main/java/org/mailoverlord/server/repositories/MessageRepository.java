package org.mailoverlord.server.repositories;

import java.util.List;

import org.mailoverlord.server.entities.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByFrom(String from);
}
