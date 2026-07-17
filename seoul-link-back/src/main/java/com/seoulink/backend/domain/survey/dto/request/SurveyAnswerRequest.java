package com.seoulink.backend.domain.survey.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 사용자가 선택한 설문 답변 요청 DTO이다.
 */
public record SurveyAnswerRequest(

        @NotNull(message = "질문 번호는 필수입니다")
        @Positive(message = "질문 번호는 1 이상이어야 합니다")
        Long questionId,

        @NotNull(message = "선택지 번호는 필수입니다")
        @Positive(message = "선택지 번호는 1 이상이어야 합니다")
        Long optionId

) {
}