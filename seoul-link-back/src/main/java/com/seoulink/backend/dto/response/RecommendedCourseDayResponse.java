package com.seoulink.backend.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class RecommendedCourseDayResponse {
    private Integer dayNo;
    private List<RecommendedCoursePlaceResponse> places;

    public RecommendedCourseDayResponse(Integer dayNo, List<RecommendedCoursePlaceResponse> places) {
        this.dayNo = dayNo;
        this.places = places;
    }
}
