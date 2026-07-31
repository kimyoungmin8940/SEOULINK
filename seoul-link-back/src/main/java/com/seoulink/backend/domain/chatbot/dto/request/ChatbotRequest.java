package com.seoulink.backend.domain.chatbot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * 클라이언트 요청 값을 검증하고 전달하는 DTO입니다.
 */
public class ChatbotRequest {
    @NotNull
    private Long memberId;

    @NotBlank
    @Size(max = 4000)
    private String question;

    @NotBlank
    @Size(max = 100)
    private String travelConcept;

    @NotBlank
    @Size(max = 36)
    private String conversationId;
}
