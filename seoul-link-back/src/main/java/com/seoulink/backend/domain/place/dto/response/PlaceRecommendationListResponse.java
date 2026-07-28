package com.seoulink.backend.domain.place.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class PlaceRecommendationListResponse {

    private final String travelCode;
    private final List<String> preferredRegions;
    private final List<PlaceRecommendationResponse> recommendedPlaces;

    public PlaceRecommendationListResponse(
            String travelCode,
            List<String> preferredRegions,
            List<PlaceRecommendationResponse> recommendedPlaces
    ) {
        this.travelCode = travelCode;
        this.preferredRegions = List.copyOf(preferredRegions);
        this.recommendedPlaces = List.copyOf(recommendedPlaces);
    }

    /** 기존 직접 생성 코드와의 호환을 유지한다. */
    public PlaceRecommendationListResponse(
            String travelCode,
            List<PlaceRecommendationResponse> recommendedPlaces
    ) {
        this(travelCode, List.of(), recommendedPlaces);
    }
}
