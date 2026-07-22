package com.seoulink.backend.domain.survey.dto.response;

import com.seoulink.backend.domain.survey.entity.SurveyOption;

//설문 선택지 조회 응답 DTO
public record SurveyOptionResponse(
        Long optionId,
        String optionText,
        String imageUrl
) {

    //SurveyOption Entity를 응답 DTO로 변환
    public static SurveyOptionResponse from(
            SurveyOption option
    ) {
        return new SurveyOptionResponse(
                option.getOptionId(),
                option.getOptionText(),
                option.getImageUrl()
        );
    }
}