package com.seoulink.backend.dto.response;

import java.util.List;

public class SurveySubmitResponse {
    private Long surveyId;
    private Long resultId;
    private String travelCode;
    private String travelTitle;
    private String description;
    private List<PlaceResponse> recommendedPlaces;
    private List<RecommendedCourseDayResponse> recommendedItinerary;

    public SurveySubmitResponse(Long surveyId, Long resultId, String travelCode,
                                String travelTitle, String description,
                                List<PlaceResponse> recommendedPlaces,
                                List<RecommendedCourseDayResponse> recommendedItinerary) {
        this.surveyId = surveyId;
        this.resultId = resultId;
        this.travelCode = travelCode;
        this.travelTitle = travelTitle;
        this.description = description;
        this.recommendedPlaces = recommendedPlaces;
        this.recommendedItinerary = recommendedItinerary;
    }

    public Long getSurveyId() { return surveyId; }
    public Long getResultId() { return resultId; }
    public String getTravelCode() { return travelCode; }
    public String getTravelTitle() { return travelTitle; }
    public String getDescription() { return description; }
    public List<PlaceResponse> getRecommendedPlaces() { return recommendedPlaces; }
    public List<RecommendedCourseDayResponse> getRecommendedItinerary() { return recommendedItinerary; }
}
