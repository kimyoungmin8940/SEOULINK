package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Haversine 계산, 좌표 검증, 외부 API 성공·실패 시 경로 행렬 선택을 검증한다. */
class DistanceServiceTest {

    private DistanceService distanceService;

    @BeforeEach
    void setUp() {
        // 기본 테스트는 API 클라이언트가 없는 fallback 계산 경로를 사용한다.
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
    @DisplayName("지도 API 호출이 실패하면 직선거리 계산으로 자동 대체한다")
    void calculateRouteMatrixFallsBackWhenApiFails() {
        OpenRouteServiceClient apiClient = mock(OpenRouteServiceClient.class);
        when(apiClient.isConfigured()).thenReturn(true);
        when(apiClient.calculateMatrix(anyList()))
                .thenThrow(new IllegalStateException("외부 API 일시 장애"));
        DistanceService serviceWithFailingApi = new DistanceService(apiClient);
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770)
        );

        DistanceService.RouteMatrix matrix =
                serviceWithFailingApi.calculateRouteMatrix(candidates);

        verify(apiClient).calculateMatrix(anyList());
        assertTrue(matrix.getDistanceKm(0, 1) > 0.0);
        assertEquals(
                matrix.getDistanceKm(0, 1) / 4.5 * 60.0,
                matrix.getTravelTimeMinutes(0, 1),
                0.000001
        );
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

    /** 거리 행렬 테스트에 필요한 최소 장소 후보를 생성한다. */
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
                .build();
    }
}
