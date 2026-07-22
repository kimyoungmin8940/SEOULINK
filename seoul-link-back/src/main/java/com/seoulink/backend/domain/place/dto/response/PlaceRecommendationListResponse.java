package com.seoulink.backend.domain.place.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class PlaceRecommendationListResponse {

    private final String travelCode;
    private final List<PlaceRecommendationResponse> recommendedPlaces;

    public PlaceRecommendationListResponse(
            String travelCode,
            List<PlaceRecommendationResponse> recommendedPlaces
    ) {
        this.travelCode = travelCode;
        this.recommendedPlaces = recommendedPlaces;
    }
}
