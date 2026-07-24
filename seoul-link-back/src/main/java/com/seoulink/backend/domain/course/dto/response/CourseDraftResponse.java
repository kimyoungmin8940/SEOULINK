package com.seoulink.backend.domain.course.dto.response;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 여행 전체 기간의 추천 코스 초안을 전달하는 응답 DTO입니다.
 *
 * 설문 번호, 설문 결과 번호, 여행 유형 코드, 동행 유형,
 * 교통수단, 여행 기간, 전체 여행 일수와 하루 시작 시간을 담습니다.
 *
 * 날짜별 목표 장소 수와 추천 후보 정보는
 * dailyPlans 목록으로 전달합니다.
 *
 * 이 클래스는 DB 테이블에 저장되는 Entity가 아니라,
 * CourseDraftService에서 계산한 결과를 Controller와 프론트에
 * 전달하는 용도로 사용합니다.
 */
@Getter
public class CourseDraftResponse {

    private final Long surveyId;
    private final Long resultId;
    private final String travelCode;
    private final String scheduleType;
    private final String companionType;
    private final String transportType;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int travelDays;
    @JsonFormat(pattern = "HH:mm")
    private final LocalTime dailyStartTime;
    private final int dailyTargetPlaceCount;
    private final Map<String, Integer> dailyCategoryTargets;

    public CourseDraftResponse(
            Long surveyId,
            Long resultId,
            String travelCode,
            String scheduleType,
            String companionType,
            String transportType,
            LocalDate startDate,
            LocalDate endDate,
            int travelDays,
            LocalTime dailyStartTime,
            int dailyTargetPlaceCount,
            Map<String, Integer> dailyCategoryTargets
    ) {
        this.surveyId = surveyId;
        this.resultId = resultId;
        this.travelCode = travelCode;
        this.scheduleType = scheduleType;
        this.companionType = companionType;
        this.transportType = transportType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.travelDays = travelDays;
        this.dailyStartTime = dailyStartTime;
        this.dailyTargetPlaceCount = dailyTargetPlaceCount;
        this.dailyCategoryTargets = new LinkedHashMap<>(dailyCategoryTargets);
    }
}
