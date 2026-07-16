package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 추천 장소의 최적화와 DB 저장을 한 번에 수행한 결과를 반환한다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRecommendResponse {

    // 방금 저장된 추천 코스의 식별자와 전체 집계값이다.
    private Long courseId;
    private String title;
    private String description;
    private String travelCode;
    private String courseType;
    private String region;
    private Boolean publicCourse;
    private Integer placeCount;
    private Integer dayCount;
    private Double totalDistanceKm;
    private Double totalTravelTimeMinutes;
    private Integer totalVisitTimeMinutes;
    private Double totalCourseTimeMinutes;

    // 추천 결과와 상세 조회가 같은 날짜별 구조를 사용하도록 통일한다.
    @Builder.Default
    private List<CourseDayResponse> days = new ArrayList<>();
}
