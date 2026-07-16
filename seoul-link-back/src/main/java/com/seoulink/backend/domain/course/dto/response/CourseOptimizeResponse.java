package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 날짜별 방문 순서 최적화 결과를 반환하는 응답 DTO이다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOptimizeResponse {

    // 날짜별 방문 순서와 장소 간 이동 정보가 계산된 결과이다.
    @Builder.Default
    private List<OptimizedPlaceDto> optimizedPlaces = new ArrayList<>();

    // 모든 날짜를 합산한 거리·이동시간·체류시간·전체 소요시간이다.
    private Double totalDistanceKm;
    private Double totalTravelTimeMinutes;
    private Integer totalVisitTimeMinutes;
    private Double totalCourseTimeMinutes;
}
