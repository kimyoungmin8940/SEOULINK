package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 사용자가 비교하고 선택할 수 있는 추천 코스 한 가지를 반환한다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOptionResponse {

    // 화면 표시 순번과 코스 생성 전략이다.
    private Integer optionNo;
    private String optionType;
    private String optionName;
    private String title;
    private String description;

    // 같은 취향으로 다시 추천할 때 이 코스 조합을 제외하는 데 사용하는 키이다.
    private String recommendationKey;

    // 이 코스에 실제 포함된 장소·일수와 전체 합계이다.
    private Integer placeCount;
    private Integer dayCount;
    private Double totalDistanceKm;
    private Double totalTravelTimeMinutes;
    private Integer totalVisitTimeMinutes;
    private Double totalCourseTimeMinutes;

    // 날짜별 방문 순서와 날짜별 합계이다.
    @Builder.Default
    private List<CourseDayResponse> days = new ArrayList<>();
}
