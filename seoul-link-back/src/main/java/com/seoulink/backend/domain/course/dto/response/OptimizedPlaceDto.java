package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 최적화가 끝난 장소 한 건과 계산된 방문 순서 정보를 표현한다.
 *
 * <p>같은 날짜의 첫 장소는 이전 장소가 없으므로
 * {@code distanceFromPreviousKm}와 {@code travelTimeFromPreviousMinutes}가 0이다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizedPlaceDto {

    private Long placeId;
    private String placeName;
    private String category;
    private Double recommendationScore;
    private Double latitude;
    private Double longitude;
    private LocalDate visitDate;
    private Integer expectedVisitMinutes;
    private Integer visitOrder;
    private Double distanceFromPreviousKm;
    private Double travelTimeFromPreviousMinutes;
}
