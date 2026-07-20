package com.seoulink.backend.domain.course.dto.response;

import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * 여행 전체 기간의 추천 코스 초안을 전달하는 응답 DTO입니다.
 *
 * 설문 번호와 여행 유형 코드, 여행 기간, 전체 여행 일수를 담고,
 * 날짜별 추천 후보 정보는 dailyPlans 목록으로 전달합니다.
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
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int travelDays;
    private final String dailyStartTime;
    private final List<DailyCourseDraftResponse> dailyPlans;

    public CourseDraftResponse(
            Long surveyId,
            Long resultId,
            String travelCode,
            LocalDate startDate,
            LocalDate endDate,
            int travelDays,
            String dailyStartTime,
            List<DailyCourseDraftResponse> dailyPlans
    ) {
        this.surveyId = surveyId;
        this.resultId = resultId;
        this.travelCode = travelCode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.travelDays = travelDays;
        this.dailyStartTime = dailyStartTime;
        this.dailyPlans = dailyPlans;
    }
}