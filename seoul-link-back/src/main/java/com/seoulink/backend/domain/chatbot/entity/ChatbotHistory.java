package com.seoulink.backend.domain.chatbot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "CHATBOT_HISTORY")
@Getter
@Setter
@NoArgsConstructor
/**
 * 데이터베이스에 저장되는 도메인 엔티티입니다.
 */
public class ChatbotHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CHAT_ID")
    private Long chatId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "PAYMENT_ID")
    private Long paymentId;

    // 같은 대화창에서 생성된 질문·답변 행을 묶는 클라이언트 UUID다.
    @Column(name = "CONVERSATION_ID", nullable = false, length = 36)
    private String conversationId;

    @Lob
    @Column(name = "QUESTION", nullable = false)
    private String question;

    @Column(name = "TRAVEL_CONCEPT", nullable = false, length = 100)
    private String travelConcept;

    @Lob
    @Column(name = "COURSE_SUMMARY")
    private String courseSummary;

    @Lob
    @Column(name = "ANSWER", nullable = false)
    private String answer;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
