package com.seoulink.backend.domain.course.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** 설문 추천 장소를 날짜별로 최적화한 결과를 반환한다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRecommendResponse {

    // 요청과 결과를 연결하는 설문 결과 식별자와 공통 일정 시작 시각이다.
    private Long resultId;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime dailyStartTime;

    // 최적화된 전체 장소 수·일수와 거리·시간 합계이다.
    private Integer placeCount;
    private Integer dayCount;
    private Double totalDistanceKm;
    private Double totalTravelTimeMinutes;
    private Integer totalVisitTimeMinutes;
    private Double totalCourseTimeMinutes;

    // 날짜별 장소 순서와 날짜별 합계이다.
    @Builder.Default
    private List<CourseDayResponse> days = new ArrayList<>();
}
