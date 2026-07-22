package com.seoulink.backend.domain.survey.dto.response;

import com.seoulink.backend.domain.survey.entity.SurveyQuestion;

import java.util.List;

//설문 질문 및 선택지 목록 조회 응답 DTO
public record SurveyQuestionResponse(
        Long questionId,
        String questionText,
        String category,
        Integer displayOrder,
        List<SurveyOptionResponse> options
) {

    //SurveyQuestion Entity를 응답 DTO로 변환
    public static SurveyQuestionResponse from(
            SurveyQuestion question
    ) {
        List<SurveyOptionResponse> optionResponses =
                question.getOptions()
                        .stream()
                        .map(SurveyOptionResponse::from)
                        .toList();

        return new SurveyQuestionResponse(
                question.getQuestionId(),
                question.getQuestionText(),
                question.getCategory(),
                question.getDisplayOrder(),
                optionResponses
        );
    }
}