package com.seoulink.backend.domain.survey.dto.response;

import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import com.seoulink.backend.domain.survey.entity.TravelTypeMaster;

import java.time.LocalDateTime;

/**
 * 비회원 설문 제출 완료 응답 DTO이다.
 */
public record GuestSurveySubmitResponse(

        Long surveyId,
        String guestToken,
        LocalDateTime expiresAt,
        SurveyResultResponse result

) {

    //저장된 설문, 설문 결과, 여행 유형을 비회원 제출 응답 DTO로 변환
    public static GuestSurveySubmitResponse from(
            TravelSurvey survey,
            SurveyResult surveyResult,
            TravelTypeMaster travelType
    ) {
        return new GuestSurveySubmitResponse(
                survey.getSurveyId(),
                survey.getGuestToken(),
                survey.getExpiresAt(),
                SurveyResultResponse.from(
                        surveyResult,
                        survey,
                        travelType
                )
        );
    }
}
