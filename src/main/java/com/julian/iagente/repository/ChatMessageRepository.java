package com.julian.iagente.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.julian.iagente.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByUserIdOrderByCreatedAtAsc(String userId);

    List<ChatMessage> findTop10ByUserIdOrderByCreatedAtDesc(String userId);
}
