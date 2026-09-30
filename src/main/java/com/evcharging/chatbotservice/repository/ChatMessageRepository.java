package com.evcharging.chatbotservice.repository;

import com.evcharging.chatbotservice.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, Long entityId);

    List<ChatMessage> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId, Pageable pageable);

    void deleteByEntityTypeAndEntityId(String entityType, Long entityId);
}
