package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistanceServiceTest {

    private DistanceService distanceService;

    @BeforeEach
    void setUp() {
        distanceService = new DistanceService();
    }

    @Test
    @DisplayName("같은 좌표 사이의 거리는 0km이다")
    void calculateDistanceKmReturnsZeroForSameCoordinates() {
        double distance = distanceService.calculateDistanceKm(
                37.5665,
                126.9780,
                37.5665,
                126.9780
        );

        assertEquals(0.0, distance, 0.000001);
    }

    @Test
    @DisplayName("서울역과 경복궁 사이의 직선거리를 계산한다")
    void calculateDistanceKmBetweenSeoulStationAndGyeongbokgung() {
        double distance = distanceService.calculateDistanceKm(
                37.5547,
                126.9706,
                37.5796,
                126.9770
        );

        assertEquals(2.83, distance, 0.05);
        assertTrue(distance >= 0.0);
    }

    @Test
    @DisplayName("API가 없으면 직선거리와 평균 도보 속도로 경로 행렬을 만든다")
    void calculateRouteMatrixUsesFallbackWithoutApiClient() {
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770)
        );

        DistanceService.RouteMatrix matrix =
                distanceService.calculateRouteMatrix(candidates);

        double distanceKm = matrix.getDistanceKm(0, 1);
        double travelTimeMinutes = matrix.getTravelTimeMinutes(0, 1);

        assertEquals(2, matrix.size());
        assertEquals(0.0, matrix.getDistanceKm(0, 0), 0.000001);
        assertTrue(distanceKm > 0.0);
        assertEquals(distanceKm / 4.5 * 60.0, travelTimeMinutes, 0.000001);
    }

    @Test
    @DisplayName("유효 범위를 벗어난 좌표는 예외가 발생한다")
    void calculateDistanceKmRejectsInvalidCoordinates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.calculateDistanceKm(
                        91.0,
                        126.9780,
                        37.5665,
                        126.9780
                )
        );
    }

    private PlaceCandidateDto place(
            Long placeId,
            String placeName,
            Double latitude,
            Double longitude
    ) {
        return PlaceCandidateDto.builder()
                .placeId(placeId)
                .placeName(placeName)
                .category("관광지")
                .recommendationScore(90.0)
                .latitude(latitude)
                .longitude(longitude)
                .visitDate(LocalDate.of(2026, 7, 20))
                .expectedVisitMinutes(60)
                .build();
    }
}
