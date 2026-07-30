package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 추천 결과와 상세 화면에서 공통으로 사용하는 날짜별 일정 응답이다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDayResponse {

    // 실제 날짜와 화면에 표시할 1일차·2일차 번호이다.
    private Integer dayNo;
    private LocalDate visitDate;

    // 해당 날짜에 포함된 장소들의 거리·시간 합계이다.
    private Double dailyDistanceKm;
    private Double dailyTravelTimeMinutes;
    private Integer dailyVisitTimeMinutes;
    private Double dailyCourseTimeMinutes;

    // 후보 부족으로 목표 장소 수를 줄였는지와 화면 안내에 필요한 수량 정보이다.
    private Boolean placeCountAdjusted;
    private String adjustmentReason;
    private String adjustmentNotice;
    private Integer requestedPlaceCount;
    private Integer actualPlaceCount;

    // 추천 화면에서 해당 DAY의 실제 경로 상세 조회를 시도했는지 나타낸다.
    private Boolean routeDetailsAttempted;

    // DAY 2 이후 전날 마지막 숙소를 DB에 중복 저장하지 않고 출발점으로 표시한다.
    private CoursePlaceResponse routeOriginPlace;

    // 해당 날짜 안에서 visitOrder 오름차순으로 정렬된 장소 목록이다.
    @Builder.Default
    private List<CoursePlaceResponse> places = new ArrayList<>();
}
