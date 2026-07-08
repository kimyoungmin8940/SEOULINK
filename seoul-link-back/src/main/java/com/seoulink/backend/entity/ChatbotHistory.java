package com.seoulink.backend.entity;

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
public class ChatbotHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CHAT_ID")
    private Long chatId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "PAYMENT_ID")
    private Long paymentId;

    @Column(name = "RESULT_ID")
    private Long resultId;

    @Column(name = "COURSE_ID")
    private Long courseId;

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
