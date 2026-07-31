package com.seoulink.backend.domain.chatbot.repository;

import com.seoulink.backend.domain.chatbot.entity.ChatbotHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 도메인 데이터를 조회하고 저장하는 리포지토리입니다.
 */
public interface ChatbotHistoryRepository extends JpaRepository<ChatbotHistory, Long> {
    List<ChatbotHistory> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<ChatbotHistory> findByMemberIdAndConversationIdOrderByCreatedAtAsc(
            Long memberId,
            String conversationId
    );

    long deleteByMemberIdAndConversationId(Long memberId, String conversationId);
}
