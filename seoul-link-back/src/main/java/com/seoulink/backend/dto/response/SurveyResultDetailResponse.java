package com.seoulink.backend.dto.response;

import com.seoulink.backend.entity.SurveyResult;
import com.seoulink.backend.entity.TravelSurvey;
import com.seoulink.backend.entity.TravelTypeMaster;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class SurveyResultDetailResponse {
    private Long resultId;
    private Long surveyId;
    private Long memberId;
    private String region;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer peopleCount;
    private String travelCode;
    private String travelTitle;
    private String description;
    private String imageUrl;
    private List<PlaceResponse> recommendedPlaces;
    private List<RecommendedCourseDayResponse> recommendedItinerary;

    public SurveyResultDetailResponse(
            SurveyResult result,
            TravelSurvey survey,
            TravelTypeMaster type,
            List<PlaceResponse> recommendedPlaces,
            List<RecommendedCourseDayResponse> recommendedItinerary
    ) {
        this.resultId = result.getResultId();
        this.surveyId = result.getSurveyId();
        this.memberId = survey.getMemberId();
        this.region = survey.getRegion();
        this.startDate = survey.getStartDate();
        this.endDate = survey.getEndDate();
        this.peopleCount = survey.getPeopleCount();
        this.travelCode = result.getTravelCode();
        this.travelTitle = type.getTypeTitle();
        this.description = type.getTypeDescription();
        this.imageUrl = type.getImageUrl();
        this.recommendedPlaces = recommendedPlaces;
        this.recommendedItinerary = recommendedItinerary;
    }
}
