package com.seoulink.backend.domain.course.dto.request;

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

    private Long placeId;
    private LocalDate visitDate;
    private Integer visitOrder;
    private Integer expectedVisitMinutes;
    private Double distanceFromPreviousKm;
    private Double travelTimeFromPreviousMinutes;
}
