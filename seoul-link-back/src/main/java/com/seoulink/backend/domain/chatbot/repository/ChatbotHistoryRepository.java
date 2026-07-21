package com.seoulink.backend.domain.chatbot.repository;

import com.seoulink.backend.domain.chatbot.entity.ChatbotHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatbotHistoryRepository extends JpaRepository<ChatbotHistory, Long> {
    List<ChatbotHistory> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}