package com.seoulink.backend.dto.response;

import com.seoulink.backend.entity.SurveyQuestion;
import lombok.Getter;

import java.util.List;

@Getter
public class SurveyQuestionResponse {
    private Long questionId;
    private String questionText;
    private String category;
    private List<SurveyOptionResponse> options;

    public SurveyQuestionResponse(Long questionId, String questionText, String category, List<SurveyOptionResponse> options) {
        this.questionId = questionId;
        this.questionText = questionText;
        this.category = category;
        this.options = options;
    }

    public SurveyQuestionResponse(SurveyQuestion question, List<SurveyOptionResponse> options) {
        this.questionId = question.getQuestionId();
        this.questionText = question.getQuestionText();
        this.category = question.getCategory();
        this.options = options;
    }
}
