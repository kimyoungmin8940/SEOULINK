package com.seoulink.backend.domain.survey.dto.response;

import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelTypeMaster;

import java.time.LocalDateTime;

/**
 * //설문 결과 조회 응답 DTO이다.
 */
public record SurveyResultResponse(

        Long resultId,
        Long surveyId,
        String travelCode,

        String typeTitle,
        String typeDescription,
        String imageUrl,

        LocalDateTime createdAt

) {

    //설문 결과와 여행 유형 엔티티를 응답 DTO로 변환
    public static SurveyResultResponse from(
            SurveyResult result,
            TravelTypeMaster travelType
    ) {
        return new SurveyResultResponse(
                result.getResultId(),
                result.getSurveyId(),
                result.getTravelCode().trim(),

                travelType.getTypeTitle(),
                travelType.getTypeDescription(),
                travelType.getImageUrl(),

                result.getCreatedAt()
        );
    }
}