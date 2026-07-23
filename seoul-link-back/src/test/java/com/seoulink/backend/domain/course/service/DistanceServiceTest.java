package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient.OdsayApiException;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient.TransitRouteResult;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
                distanceService.calculateRouteMatrix(
                        candidates,
                        TransportMode.WALKING
                );

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
        when(apiClient.calculateMatrix(eq("foot-walking"), anyList()))
                .thenThrow(new IllegalStateException("외부 API 일시 장애"));
        DistanceService serviceWithFailingApi = new DistanceService(apiClient);
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770)
        );

        DistanceService.RouteMatrix matrix =
                serviceWithFailingApi.calculateRouteMatrix(
                        candidates,
                        TransportMode.WALKING
                );

        verify(apiClient).calculateMatrix(eq("foot-walking"), anyList());
        assertTrue(matrix.getDistanceKm(0, 1) > 0.0);
        assertEquals(
                matrix.getDistanceKm(0, 1) / 4.5 * 60.0,
                matrix.getTravelTimeMinutes(0, 1),
                0.000001
        );
    }

    @Test
    @DisplayName("외부 API 실패로 만든 추정 경로는 장기 캐시에 저장하지 않는다")
    void calculateRouteMatrixDoesNotCacheEstimatedPairs() {
        OpenRouteServiceClient apiClient = mock(OpenRouteServiceClient.class);
        RoutePairCache cache = new RoutePairCache(100, Duration.ofHours(1));
        DistanceService service = new DistanceService(apiClient, cache);
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770)
        );

        when(apiClient.isConfigured()).thenReturn(true);
        when(apiClient.calculateMatrix(eq("foot-walking"), anyList()))
                .thenThrow(new IllegalStateException("외부 API 일시 장애"));

        service.calculateRouteMatrix(candidates, TransportMode.WALKING);
        service.calculateRouteMatrix(candidates, TransportMode.WALKING);

        assertEquals(0, cache.size());
        verify(apiClient, times(2)).calculateMatrix(
                eq("foot-walking"),
                anyList()
        );
    }


    @Test
    @DisplayName("동일 장소 쌍은 코스 옵션이 달라도 외부 API 결과를 캐시해 재사용한다")
    void calculateRouteMatrixReusesCachedPlacePairs() {
        OpenRouteServiceClient apiClient = mock(OpenRouteServiceClient.class);
        RoutePairCache cache = new RoutePairCache(100, Duration.ofHours(1));
        DistanceService cachedService = new DistanceService(apiClient, cache);
        PlaceCandidateDto cityHall = place(1L, "서울시청", 37.5665, 126.9780);
        PlaceCandidateDto palace = place(2L, "경복궁", 37.5796, 126.9770);

        when(apiClient.isConfigured()).thenReturn(true);
        when(apiClient.calculateMatrix(eq("foot-walking"), anyList())).thenReturn(
                new OpenRouteServiceClient.RouteMatrixResult(
                        new double[][]{{0.0, 1.2}, {1.4, 0.0}},
                        new double[][]{{0.0, 16.0}, {18.0, 0.0}}
                )
        );

        DistanceService.RouteMatrix first = cachedService.calculateRouteMatrix(
                List.of(cityHall, palace),
                TransportMode.WALKING
        );
        DistanceService.RouteMatrix reversed = cachedService.calculateRouteMatrix(
                List.of(palace, cityHall),
                TransportMode.WALKING
        );

        verify(apiClient, times(1)).calculateMatrix(
                eq("foot-walking"),
                anyList()
        );
        assertEquals(2, cache.size());
        assertEquals(1.2, first.getDistanceKm(0, 1), 0.000001);
        assertEquals(1.4, reversed.getDistanceKm(0, 1), 0.000001);
        assertEquals(18.0, reversed.getTravelTimeMinutes(0, 1), 0.000001);
    }

    @Test
    @DisplayName("같은 장소 쌍도 이동수단별 캐시와 이동시간을 분리한다")
    void calculateRouteMatrixSeparatesCacheByTransportMode() {
        OpenRouteServiceClient apiClient = mock(OpenRouteServiceClient.class);
        RoutePairCache cache = new RoutePairCache(100, Duration.ofHours(1));
        DistanceService cachedService = new DistanceService(apiClient, cache);
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770)
        );

        when(apiClient.isConfigured()).thenReturn(true);
        when(apiClient.calculateMatrix(anyString(), anyList())).thenAnswer(invocation -> {
            String profile = invocation.getArgument(0);
            double minutes = "driving-car".equals(profile) ? 6.0 : 18.0;
            return new OpenRouteServiceClient.RouteMatrixResult(
                    new double[][]{{0.0, 1.2}, {1.2, 0.0}},
                    new double[][]{{0.0, minutes}, {minutes, 0.0}}
            );
        });

        DistanceService.RouteMatrix walking = cachedService.calculateRouteMatrix(
                candidates,
                TransportMode.WALKING
        );
        DistanceService.RouteMatrix driving = cachedService.calculateRouteMatrix(
                candidates,
                TransportMode.DRIVING
        );

        assertEquals(18.0, walking.getTravelTimeMinutes(0, 1), 0.000001);
        assertEquals(6.0, driving.getTravelTimeMinutes(0, 1), 0.000001);
        assertEquals(4, cache.size());
        verify(apiClient).calculateMatrix(eq("foot-walking"), anyList());
        verify(apiClient).calculateMatrix(eq("driving-car"), anyList());
    }

    @Test
    @DisplayName("대중교통은 도보 시간을 재사용하지 않고 별도 추정값으로 표시한다")
    void publicTransitUsesSeparateEstimatedCalculator() {
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770)
        );

        DistanceService.RouteMatrix walking = distanceService.calculateRouteMatrix(
                candidates,
                TransportMode.WALKING
        );
        DistanceService.RouteMatrix transit = distanceService.calculateRouteMatrix(
                candidates,
                TransportMode.PUBLIC_TRANSIT
        );

        assertTrue(transit.estimatedTravelTimes());
        assertTrue(walking.estimatedTravelTimes());
        assertTrue(Math.abs(
                walking.getTravelTimeMinutes(0, 1)
                        - transit.getTravelTimeMinutes(0, 1)
        ) > 0.000001);
    }

    @Test
    @DisplayName("ODsay가 설정되면 대중교통 거리와 시간을 방향별 실제 결과로 사용한다")
    void publicTransitUsesOdsayResultsWhenConfigured() {
        OdsayClient odsayClient = mock(OdsayClient.class);
        RoutePairCache cache = new RoutePairCache(100, Duration.ofHours(1));
        DistanceService serviceWithOdsay = new DistanceService(
                null,
                odsayClient,
                cache
        );
        PlaceCandidateDto cityHall = place(
                1L,
                "서울시청",
                37.5665,
                126.9780
        );
        PlaceCandidateDto palace = place(
                2L,
                "경복궁",
                37.5796,
                126.9770
        );

        when(odsayClient.isConfigured()).thenReturn(true);
        when(odsayClient.calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
        )).thenAnswer(invocation -> {
            RouteCoordinate from = invocation.getArgument(0);
            boolean cityHallToPalace = Double.compare(
                    from.longitude(),
                    cityHall.getLongitude()
            ) == 0;
            return cityHallToPalace
                    ? new TransitRouteResult(
                            1.3,
                            14.0,
                            TransitPathType.SUBWAY
                    )
                    : new TransitRouteResult(
                            1.5,
                            17.0,
                            TransitPathType.BUS_SUBWAY
                    );
        });

        DistanceService.RouteMatrix first = serviceWithOdsay.calculateRouteMatrix(
                List.of(cityHall, palace),
                TransportMode.PUBLIC_TRANSIT
        );
        DistanceService.RouteMatrix cachedReverse =
                serviceWithOdsay.calculateRouteMatrix(
                        List.of(palace, cityHall),
                        TransportMode.PUBLIC_TRANSIT
                );

        assertFalse(first.estimatedTravelTimes());
        assertEquals(1.3, first.getDistanceKm(0, 1), 0.000001);
        assertEquals(14.0, first.getTravelTimeMinutes(0, 1), 0.000001);
        assertEquals(TransitPathType.SUBWAY, first.getTransitPathType(0, 1));
        assertEquals(1.5, cachedReverse.getDistanceKm(0, 1), 0.000001);
        assertEquals(17.0, cachedReverse.getTravelTimeMinutes(0, 1), 0.000001);
        assertEquals(
                TransitPathType.BUS_SUBWAY,
                cachedReverse.getTransitPathType(0, 1)
        );
        assertEquals(2, cache.size());
        verify(odsayClient, times(2)).calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
        );
    }

    @Test
    @DisplayName("대중교통 후보 풀 1차 평가는 ODsay 호출 없이 전용 추정행렬을 사용한다")
    void publicTransitCandidatePoolUsesEstimateWithoutOdsayCalls() {
        OdsayClient odsayClient = mock(OdsayClient.class);
        DistanceService serviceWithOdsay = new DistanceService(
                null,
                odsayClient,
                new RoutePairCache(100, Duration.ofHours(1))
        );
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770)
        );

        when(odsayClient.isConfigured()).thenReturn(true);

        DistanceService.RouteMatrix matrix =
                serviceWithOdsay.calculateCandidatePoolMatrix(
                        candidates,
                        TransportMode.PUBLIC_TRANSIT
                );

        assertTrue(matrix.estimatedTravelTimes());
        assertTrue(matrix.getDistanceKm(0, 1) > 0.0);
        verify(odsayClient, times(0)).calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
        );
    }

    @Test
    @DisplayName("도보와 자동차 후보 풀도 ORS 호출 없이 전용 추정행렬을 사용한다")
    void walkingAndDrivingCandidatePoolsDoNotCallOpenRouteService() {
        OpenRouteServiceClient apiClient = mock(OpenRouteServiceClient.class);
        DistanceService service = new DistanceService(
                apiClient,
                new RoutePairCache(100, Duration.ofHours(1))
        );
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770)
        );

        when(apiClient.isConfigured()).thenReturn(true);

        DistanceService.RouteMatrix walking =
                service.calculateCandidatePoolMatrix(
                        candidates,
                        TransportMode.WALKING
                );
        DistanceService.RouteMatrix driving =
                service.calculateCandidatePoolMatrix(
                        candidates,
                        TransportMode.DRIVING
                );

        assertTrue(walking.estimatedTravelTimes());
        assertTrue(driving.estimatedTravelTimes());
        verify(apiClient, times(0)).calculateMatrix(
                anyString(),
                anyList()
        );
    }

    @Test
    @DisplayName("카드의 도보 DAY는 여러 인접 구간을 ORS Matrix 한 번으로 조회한다")
    void walkingVisibleDayUsesOneOpenRouteMatrixRequest() {
        OpenRouteServiceClient apiClient = mock(OpenRouteServiceClient.class);
        DistanceService service = new DistanceService(
                apiClient,
                new RoutePairCache(100, Duration.ofHours(1))
        );
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770),
                place(3L, "광장시장", 37.5700, 126.9997)
        );
        double[][] distances = {
                {0.0, 1.2, 2.0},
                {1.3, 0.0, 1.1},
                {2.1, 1.0, 0.0}
        };
        double[][] minutes = {
                {0.0, 16.0, 27.0},
                {17.0, 0.0, 15.0},
                {28.0, 14.0, 0.0}
        };

        when(apiClient.isConfigured()).thenReturn(true);
        when(apiClient.calculateMatrix(eq("foot-walking"), anyList()))
                .thenReturn(new OpenRouteServiceClient.RouteMatrixResult(
                        distances,
                        minutes
                ));

        DistanceService.RouteMatrix matrix =
                service.calculateRouteLegMatrix(
                        candidates,
                        TransportMode.WALKING,
                        List.of(0, 1, 2)
                );

        assertFalse(matrix.isEstimated(0, 1));
        assertFalse(matrix.isEstimated(1, 2));
        assertEquals(16.0, matrix.getTravelTimeMinutes(0, 1), 0.000001);
        assertEquals(15.0, matrix.getTravelTimeMinutes(1, 2), 0.000001);
        verify(apiClient, times(1)).calculateMatrix(
                eq("foot-walking"),
                anyList()
        );
    }

    @Test
    @DisplayName("최종 대중교통 경로는 방문 순서의 인접 구간만 ODsay로 조회한다")
    void publicTransitFinalRouteRequestsOnlyAdjacentLegs() {
        OdsayClient odsayClient = mock(OdsayClient.class);
        DistanceService serviceWithOdsay = new DistanceService(
                null,
                odsayClient,
                new RoutePairCache(100, Duration.ofHours(1))
        );
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770),
                place(3L, "광장시장", 37.5700, 126.9997)
        );

        when(odsayClient.isConfigured()).thenReturn(true);
        when(odsayClient.calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
        )).thenReturn(new TransitRouteResult(
                1.2,
                12.0,
                TransitPathType.BUS
        ));

        DistanceService.RouteMatrix matrix =
                serviceWithOdsay.calculateRouteLegMatrix(
                        candidates,
                        TransportMode.PUBLIC_TRANSIT,
                        List.of(0, 2, 1)
                );

        assertFalse(matrix.isEstimated(0, 2));
        assertFalse(matrix.isEstimated(2, 1));
        assertEquals(TransitPathType.BUS, matrix.getTransitPathType(0, 2));
        assertEquals(TransitPathType.BUS, matrix.getTransitPathType(2, 1));
        verify(odsayClient, times(2)).calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
        );
    }

    @Test
    @DisplayName("대중교통 후보가 추가되어도 캐시에 있는 장소 쌍은 ODsay를 재호출하지 않는다")
    void publicTransitRequestsOnlyPairsMissingFromCache() {
        OdsayClient odsayClient = mock(OdsayClient.class);
        RoutePairCache cache = new RoutePairCache(100, Duration.ofHours(1));
        DistanceService serviceWithOdsay = new DistanceService(
                null,
                odsayClient,
                cache
        );
        PlaceCandidateDto cityHall = place(
                1L,
                "서울시청",
                37.5665,
                126.9780
        );
        PlaceCandidateDto palace = place(
                2L,
                "경복궁",
                37.5796,
                126.9770
        );
        PlaceCandidateDto market = place(
                3L,
                "광장시장",
                37.5700,
                126.9997
        );

        when(odsayClient.isConfigured()).thenReturn(true);
        when(odsayClient.calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
        )).thenReturn(new TransitRouteResult(
                1.0,
                10.0,
                TransitPathType.BUS
        ));

        serviceWithOdsay.calculateRouteMatrix(
                List.of(cityHall, palace),
                TransportMode.PUBLIC_TRANSIT
        );
        serviceWithOdsay.calculateRouteMatrix(
                List.of(cityHall, palace, market),
                TransportMode.PUBLIC_TRANSIT
        );

        assertEquals(6, cache.size());
        verify(odsayClient, times(6)).calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
        );
    }

    @Test
    @DisplayName("ODsay 700m 이내 오류 구간은 도보 추정시간으로 처리한다")
    void publicTransitUsesWalkingEstimateForShortOdsayLeg() {
        OdsayClient odsayClient = mock(OdsayClient.class);
        DistanceService serviceWithOdsay = new DistanceService(
                null,
                odsayClient,
                new RoutePairCache(100, Duration.ofHours(1))
        );
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "덕수궁", 37.5658, 126.9751)
        );

        when(odsayClient.isConfigured()).thenReturn(true);
        when(odsayClient.calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
        )).thenThrow(new OdsayApiException(
                "-98",
                "출, 도착지가 700m이내입니다."
        ));

        DistanceService.RouteMatrix matrix =
                serviceWithOdsay.calculateRouteMatrix(
                        candidates,
                        TransportMode.PUBLIC_TRANSIT
                );

        assertTrue(matrix.estimatedTravelTimes());
        assertEquals(
                matrix.getDistanceKm(0, 1) / 4.5 * 60.0,
                matrix.getTravelTimeMinutes(0, 1),
                0.000001
        );
        assertEquals(
                TransitPathType.WALKING,
                matrix.getTransitPathType(0, 1)
        );
        verify(odsayClient, times(2)).calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
        );
    }

    @Test
    @DisplayName("ODsay 인증 오류가 발생하면 같은 행렬의 남은 구간은 재호출하지 않는다")
    void publicTransitStopsMatrixCallsAfterFatalOdsayError() {
        OdsayClient odsayClient = mock(OdsayClient.class);
        DistanceService serviceWithOdsay = new DistanceService(
                null,
                odsayClient,
                new RoutePairCache(100, Duration.ofHours(1))
        );
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "서울시청", 37.5665, 126.9780),
                place(2L, "경복궁", 37.5796, 126.9770),
                place(3L, "광장시장", 37.5700, 126.9997)
        );

        when(odsayClient.isConfigured()).thenReturn(true);
        when(odsayClient.calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
        )).thenThrow(new OdsayApiException(
                "500",
                "ApiKey authentication failed."
        ));

        DistanceService.RouteMatrix matrix =
                serviceWithOdsay.calculateRouteMatrix(
                        candidates,
                        TransportMode.PUBLIC_TRANSIT
                );

        assertTrue(matrix.estimatedTravelTimes());
        verify(odsayClient, times(1)).calculateRoute(
                any(RouteCoordinate.class),
                any(RouteCoordinate.class)
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

    @Test
    @DisplayName("NaN 또는 무한대 좌표는 예외가 발생한다")
    void calculateDistanceKmRejectsNonFiniteCoordinates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.calculateDistanceKm(
                        37.5665,
                        Double.NaN,
                        37.5796,
                        126.9770
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> distanceService.calculateDistanceKm(
                        37.5665,
                        126.9780,
                        Double.POSITIVE_INFINITY,
                        126.9770
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
