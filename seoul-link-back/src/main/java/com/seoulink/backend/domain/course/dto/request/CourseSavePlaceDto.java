package com.seoulink.backend.domain.course.dto.request;

import com.seoulink.backend.domain.course.model.TransitPathType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 최적화 결과 중 COURSE_DETAILS 저장에 필요한 장소 정보만 전달한다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSavePlaceDto {

    // 장소 식별자와 날짜별 방문 순서이다.
    private Long placeId;
    private String category;
    private Integer dayNo;
    private LocalDate visitDate;
    private Integer visitOrder;
    private String visitTime;

    // 해당 장소의 체류시간과 바로 이전 장소에서 이동해 온 거리·시간이다.
    // DAY 1 첫 장소는 0/null이며, DAY 2 이후 전날 숙소에서 출발한 첫 일반
    // 장소에는 숙소→장소 구간의 실제 경로값이 들어갈 수 있다.
    private Integer expectedVisitMinutes;
    private Double distanceFromPreviousKm;
    private Double travelTimeFromPreviousMinutes;
    private TransitPathType transitPathType;
    private Boolean routeEstimated;
}
