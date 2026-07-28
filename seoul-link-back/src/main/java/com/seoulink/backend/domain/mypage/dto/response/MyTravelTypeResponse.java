package com.seoulink.backend.domain.mypage.dto.response;

import lombok.Getter;

@Getter
public class MyTravelTypeResponse {
    private Long surveyId;
    private Long resultId;
    private String travelCode;
    private String typeTitle;
    private String typeDescription;
    private String imageUrl;

    public MyTravelTypeResponse(
            Long surveyId,
            Long resultId,
            String travelCode,
            String typeTitle,
            String typeDescription,
            String imageUrl
    ) {
        this.surveyId = surveyId;
        this.resultId = resultId;
        this.travelCode = travelCode;
        this.typeTitle = typeTitle;
        this.typeDescription = typeDescription;
        this.imageUrl = imageUrl;
    }
}
