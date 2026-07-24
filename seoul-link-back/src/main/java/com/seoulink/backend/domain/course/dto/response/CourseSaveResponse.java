package com.seoulink.backend.domain.course.dto.response;

import com.seoulink.backend.domain.course.model.TransportMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 코스와 상세 장소가 DB에 저장된 결과를 반환한다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSaveResponse {

    // 저장 결과 확인에 필요한 코스 식별자와 서버가 다시 계산한 집계값이다.
    private Long courseId;
    private String title;
    private TransportMode transportMode;
    private Integer placeCount;
    private Integer dayCount;
    private Double totalDistanceKm;
    private Double totalTravelTimeMinutes;
    private Integer totalVisitTimeMinutes;
    private Double totalCourseTimeMinutes;
}
