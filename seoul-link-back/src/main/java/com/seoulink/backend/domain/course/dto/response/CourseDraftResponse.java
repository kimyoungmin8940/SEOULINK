package com.seoulink.backend.domain.course.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 여행 전체 기간의 추천 코스 초안을 전달하는 응답 DTO입니다.
 *
 * <p>설문 메타데이터와 함께 추천 API가 바로 사용할 숙소 후보 및 날짜별 장소 후보를
 * 전달합니다. {@code dailyPlans}는 비어 있지 않아야 하며 각 날짜의 후보 수와
 * {@code categoryTargets}는 서로 일치합니다.</p>
 */
@Getter
public class CourseDraftResponse {

    private final Long surveyId;
    private final Long resultId;
    private final String travelCode;
    private final String scheduleType;
    private final String companionType;
    private final String transportType;
    private final List<String> preferredRegions;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int travelDays;

    @JsonFormat(pattern = "HH:mm")
    private final LocalTime dailyStartTime;

    private final int dailyTargetPlaceCount;
    private final Map<String, Integer> dailyCategoryTargets;
    private final List<PlaceRecommendationResponse> hotelCandidates;
    private final List<DailyCourseDraftResponse> dailyPlans;

    public List<PlaceRecommendationResponse> getHotelCandidates() {
        return hotelCandidates;
    }

    public List<DailyCourseDraftResponse> getDailyPlans() {
        return dailyPlans;
    }

    public CourseDraftResponse(
            Long surveyId,
            Long resultId,
            String travelCode,
            String scheduleType,
            String companionType,
            String transportType,
            List<String> preferredRegions,
            LocalDate startDate,
            LocalDate endDate,
            int travelDays,
            LocalTime dailyStartTime,
            int dailyTargetPlaceCount,
            Map<String, Integer> dailyCategoryTargets,
            List<PlaceRecommendationResponse> hotelCandidates,
            List<DailyCourseDraftResponse> dailyPlans
    ) {
        this.surveyId = surveyId;
        this.resultId = resultId;
        this.travelCode = travelCode;
        this.scheduleType = scheduleType;
        this.companionType = companionType;
        this.transportType = transportType;
        this.preferredRegions = List.copyOf(preferredRegions);
        this.startDate = startDate;
        this.endDate = endDate;
        this.travelDays = travelDays;
        this.dailyStartTime = dailyStartTime;
        this.dailyTargetPlaceCount = dailyTargetPlaceCount;
        this.dailyCategoryTargets = new LinkedHashMap<>(dailyCategoryTargets);
        this.hotelCandidates = List.copyOf(hotelCandidates);
        this.dailyPlans = List.copyOf(dailyPlans);
    }
}
