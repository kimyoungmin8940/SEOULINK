package com.seoulink.backend.repository;

import com.seoulink.backend.entity.ChatbotHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatbotHistoryRepository extends JpaRepository<ChatbotHistory, Long> {
    List<ChatbotHistory> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}