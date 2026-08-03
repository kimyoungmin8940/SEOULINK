package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.exception.PublicTransitMinimumPlaceException;
import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.model.TransitPathType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 날짜 분리, 중복 제거, 2-opt 경로 개선, 선택 우선순위와 입력값 검증을 확인한다. */
class CourseOptimizationServiceTest {

    private CourseOptimizationService courseOptimizationService;

    @BeforeEach
    void setUp() {
        // 외부 경로 API가 없는 DistanceService를 사용해 테스트 결과를 결정적으로 유지한다.
        courseOptimizationService = new CourseOptimizationService(
                new DistanceService(),
                new VisitDurationService()
        );
    }

    @Test
    @DisplayName("장소를 날짜별로 나누고 이동시간이 짧은 순서로 정렬한다")
    void optimizePlacesByDateAndTravelTime() {
        LocalDate firstDay = LocalDate.of(2026, 7, 20);
        LocalDate secondDay = LocalDate.of(2026, 7, 21);

        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(6L, "연남동", "카페", 75.0, 37.5660, 126.9250, secondDay),
                        place(4L, "남산서울타워", "관광지", 80.0, 37.5512, 126.9882, firstDay),
                        place(2L, "덕수궁", "관광지", 70.0, 37.5658, 126.9751, firstDay),
                        place(5L, "홍대입구", "관광지", 95.0, 37.5572, 126.9254, secondDay),
                        place(3L, "경복궁", "관광지", 90.0, 37.5796, 126.9770, firstDay),
                        place(1L, "서울시청", "관광지", 100.0, 37.5665, 126.9780, firstDay)
                ))
                .build();

        CourseOptimizeResponse response = courseOptimizationService.optimize(request);
        List<OptimizedPlaceDto> result = response.getOptimizedPlaces();

        assertEquals(
                List.of(1L, 3L, 2L, 4L, 5L, 6L),
                result.stream().map(OptimizedPlaceDto::getPlaceId).toList()
        );
        assertEquals(
                List.of(1, 2, 3, 4, 1, 2),
                result.stream().map(OptimizedPlaceDto::getVisitOrder).toList()
        );
        assertEquals(firstDay, result.get(0).getVisitDate());
        assertEquals(secondDay, result.get(4).getVisitDate());
        assertEquals(0.0, result.get(0).getDistanceFromPreviousKm(), 0.000001);
        assertEquals(0.0, result.get(4).getDistanceFromPreviousKm(), 0.000001);
        assertEquals(0.0, result.get(0).getTravelTimeFromPreviousMinutes(), 0.000001);
        assertEquals(0.0, result.get(4).getTravelTimeFromPreviousMinutes(), 0.000001);
        assertEquals(
                List.of(90, 90, 90, 90, 90, 60),
                result.stream().map(OptimizedPlaceDto::getExpectedVisitMinutes).toList()
        );

        double distanceSum = result.stream()
                .mapToDouble(OptimizedPlaceDto::getDistanceFromPreviousKm)
                .sum();
        double travelTimeSum = result.stream()
                .mapToDouble(OptimizedPlaceDto::getTravelTimeFromPreviousMinutes)
                .sum();

        assertEquals(distanceSum, response.getTotalDistanceKm(), 0.000001);
        assertEquals(travelTimeSum, response.getTotalTravelTimeMinutes(), 0.000001);
        assertEquals(510, response.getTotalVisitTimeMinutes());
        assertEquals(
                510.0 + travelTimeSum,
                response.getTotalCourseTimeMinutes(),
                0.000001
        );
        assertTrue(response.getTotalDistanceKm() > 0.0);
        assertTrue(response.getTotalTravelTimeMinutes() > 0.0);
    }

    @Test
    @DisplayName("2-opt는 첫 장소를 유지하면서 최근접 이웃 경로의 총 이동시간을 줄인다")
    void improveNearestNeighborRouteWithTwoOpt() {
        double[][] travelTimes = {
                {0.0, 1.0, 2.0, 8.0},
                {1.0, 0.0, 1.0, 2.0},
                {2.0, 1.0, 0.0, 10.0},
                {8.0, 2.0, 10.0, 0.0}
        };
        double[][] distances = {
                {0.0, 1.0, 2.0, 8.0},
                {1.0, 0.0, 1.0, 2.0},
                {2.0, 1.0, 0.0, 10.0},
                {8.0, 2.0, 10.0, 0.0}
        };
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteMatrix(
                anyList(),
                eq(TransportMode.WALKING)
        ))
                .thenReturn(new DistanceService.RouteMatrix(
                        distances,
                        travelTimes
                ));
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "고정 출발", "TOUR", 100.0,
                                37.5, 126.9, visitDate),
                        place(2L, "두 번째 후보", "TOUR", 90.0,
                                37.51, 126.91, visitDate),
                        place(3L, "세 번째 후보", "TOUR", 80.0,
                                37.52, 126.92, visitDate),
                        place(4L, "네 번째 후보", "TOUR", 70.0,
                                37.53, 126.93, visitDate)
                ))
                .build();

        CourseOptimizeResponse response = service.optimize(request);

        // 최근접 이웃 1-2-3-4(12분)를 1-3-2-4(5분)로 개선한다.
        assertEquals(
                List.of(1L, 3L, 2L, 4L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(5.0, response.getTotalTravelTimeMinutes(), 0.000001);
        assertEquals(5.0, response.getTotalDistanceKm(), 0.000001);
        assertEquals(365.0, response.getTotalCourseTimeMinutes(), 0.000001);
    }

    @Test
    @DisplayName("최종 방문 경로에서 사용하지 않은 추정 구간은 결과를 추정값으로 표시하지 않는다")
    void estimatedFlagChecksOnlyFinalRouteLegs() {
        double[][] travelTimes = {
                {0.0, 1.0, 10.0},
                {1.0, 0.0, 1.0},
                {10.0, 1.0, 0.0}
        };
        double[][] distances = {
                {0.0, 1.0, 10.0},
                {1.0, 0.0, 1.0},
                {10.0, 1.0, 0.0}
        };
        boolean[][] estimatedPairs = {
                {false, false, true},
                {false, false, false},
                {false, false, false}
        };
        TransitPathType[][] transitPathTypes = {
                {null, TransitPathType.SUBWAY, null},
                {TransitPathType.SUBWAY, null, TransitPathType.BUS_SUBWAY},
                {null, TransitPathType.BUS_SUBWAY, null}
        };
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateCandidatePoolMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT)
        )).thenReturn(new DistanceService.RouteMatrix(
                distances,
                travelTimes,
                estimatedPairs,
                transitPathTypes
        ));
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        )).thenReturn(new DistanceService.RouteMatrix(
                distances,
                travelTimes,
                estimatedPairs,
                transitPathTypes
        ));
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .placeCandidates(List.of(
                        place(1L, "출발", "TOUR", 100.0,
                                37.5, 126.9, visitDate),
                        place(2L, "중간", "TOUR", 90.0,
                                37.51, 126.91, visitDate),
                        place(3L, "도착", "TOUR", 80.0,
                                37.52, 126.92, visitDate)
                ))
                .build();

        CourseOptimizeResponse response = service.optimize(request);

        assertEquals(
                List.of(1L, 2L, 3L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertFalse(response.getEstimatedTravelTimes());
        assertEquals(
                List.of(TransitPathType.SUBWAY, TransitPathType.BUS_SUBWAY),
                response.getOptimizedPlaces().stream()
                        .skip(1)
                        .map(OptimizedPlaceDto::getTransitPathType)
                        .toList()
        );
    }

    @Test
    @DisplayName("총 이동시간이 같으면 2-opt는 총 거리가 더 짧은 경로를 선택한다")
    void useDistanceAsTwoOptTieBreaker() {
        double[][] travelTimes = {
                {0.0, 1.0, 1.0, 1.0},
                {1.0, 0.0, 1.0, 1.0},
                {1.0, 1.0, 0.0, 1.0},
                {1.0, 1.0, 1.0, 0.0}
        };
        double[][] distances = {
                {0.0, 1.0, 2.0, 8.0},
                {1.0, 0.0, 1.0, 2.0},
                {2.0, 1.0, 0.0, 10.0},
                {8.0, 2.0, 10.0, 0.0}
        };
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteMatrix(
                anyList(),
                eq(TransportMode.WALKING)
        ))
                .thenReturn(new DistanceService.RouteMatrix(
                        distances,
                        travelTimes
                ));
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "고정 출발", "TOUR", 100.0,
                                37.5, 126.9, visitDate),
                        place(2L, "두 번째 후보", "TOUR", 90.0,
                                37.51, 126.91, visitDate),
                        place(3L, "세 번째 후보", "TOUR", 80.0,
                                37.52, 126.92, visitDate),
                        place(4L, "네 번째 후보", "TOUR", 70.0,
                                37.53, 126.93, visitDate)
                ))
                .build();

        CourseOptimizeResponse response = service.optimize(request);

        assertEquals(
                List.of(1L, 3L, 2L, 4L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(3.0, response.getTotalTravelTimeMinutes(), 0.000001);
        assertEquals(5.0, response.getTotalDistanceKm(), 0.000001);
    }

    @Test
    @DisplayName("장소 후보가 비어 있으면 빈 최적화 결과를 반환한다")
    void optimizeReturnsEmptyResponseForEmptyCandidates() {
        CourseOptimizeResponse response = courseOptimizationService.optimize(
                CourseOptimizeRequest.builder()
                        .transportMode(TransportMode.WALKING)
                        .build()
        );

        assertTrue(response.getOptimizedPlaces().isEmpty());
        assertEquals(0.0, response.getTotalDistanceKm(), 0.000001);
        assertEquals(0.0, response.getTotalTravelTimeMinutes(), 0.000001);
        assertEquals(0, response.getTotalVisitTimeMinutes());
        assertEquals(0.0, response.getTotalCourseTimeMinutes(), 0.000001);
    }

    @Test
    @DisplayName("이동수단이 없으면 최적화 요청을 거부한다")
    void optimizeRejectsMissingTransportMode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseOptimizationService.optimize(
                        CourseOptimizeRequest.builder().build()
                )
        );

        assertTrue(exception.getMessage().contains("이동수단"));
    }

    @Test
    @DisplayName("장소가 한 개면 이동거리와 이동시간 없이 방문 순서 1로 반환한다")
    void optimizeSingleCandidate() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(place(
                        1L,
                        "경복궁",
                        "TOUR",
                        90.0,
                        37.5796,
                        126.9770,
                        visitDate
                )))
                .build();

        CourseOptimizeResponse response = courseOptimizationService.optimize(request);
        OptimizedPlaceDto optimizedPlace = response.getOptimizedPlaces().get(0);

        assertEquals(1, response.getOptimizedPlaces().size());
        assertEquals(1L, optimizedPlace.getPlaceId());
        assertEquals(1, optimizedPlace.getVisitOrder());
        assertEquals(0.0, optimizedPlace.getDistanceFromPreviousKm(), 0.000001);
        assertEquals(0.0, optimizedPlace.getTravelTimeFromPreviousMinutes(), 0.000001);
        assertEquals(90, response.getTotalVisitTimeMinutes());
        assertEquals(90.0, response.getTotalCourseTimeMinutes(), 0.000001);
    }

    @Test
    @DisplayName("좌표가 누락된 장소 후보는 예외가 발생한다")
    void optimizeRejectsCandidateWithoutCoordinates() {
        PlaceCandidateDto invalidCandidate = place(
                1L,
                "좌표 없는 장소",
                "TOUR",
                90.0,
                null,
                126.9780,
                LocalDate.of(2026, 7, 20)
        );

        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(invalidCandidate))
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseOptimizationService.optimize(request)
        );

        assertEquals("장소의 위도와 경도는 필수입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("유효 범위를 벗어난 좌표가 있으면 최적화를 중단한다")
    void optimizeRejectsOutOfRangeCoordinates() {
        PlaceCandidateDto invalidCandidate = place(
                1L,
                "잘못된 좌표의 장소",
                "TOUR",
                90.0,
                91.0,
                126.9780,
                LocalDate.of(2026, 7, 20)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseOptimizationService.optimize(
                        CourseOptimizeRequest.builder()
                                .transportMode(TransportMode.WALKING)
                                .placeCandidates(List.of(invalidCandidate))
                                .build()
                )
        );

        assertEquals(
                "위도는 -90 이상 90 이하의 유한한 숫자여야 합니다.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("대체 후보의 좌표가 누락되어도 최적화를 중단한다")
    void optimizeRejectsAlternativeWithoutCoordinates() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        PlaceCandidateDto original = place(
                1L, "서울시청", "TOUR", 100.0,
                37.5665, 126.9780, visitDate
        );
        original.setAlternativeCandidates(List.of(place(
                2L, "좌표 없는 대체 후보", "TOUR", 90.0,
                null, 126.9751, null
        )));

        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(original))
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseOptimizationService.optimize(request)
        );

        assertEquals("장소의 위도와 경도는 필수입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("중복 장소는 한 번만 남기고 같은 날짜에서는 높은 추천 점수를 사용한다")
    void optimizeRemovesDuplicatePlaces() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "경복궁", "TOUR", 80.0,
                                37.5796, 126.9770, visitDate),
                        place(1L, "경복궁", "TOUR", 95.0,
                                37.5796, 126.9770, visitDate),
                        place(2L, "덕수궁", "TOUR", 90.0,
                                37.5658, 126.9751, visitDate)
                ))
                .build();

        CourseOptimizeResponse response = courseOptimizationService.optimize(request);

        assertEquals(2, response.getOptimizedPlaces().size());
        assertEquals(1L, response.getOptimizedPlaces().get(0).getPlaceId());
        assertEquals(
                95.0,
                response.getOptimizedPlaces().get(0).getRecommendationScore(),
                0.000001
        );
        assertEquals(1, response.getOptimizedPlaces().stream()
                .filter(place -> place.getPlaceId().equals(1L))
                .count());
    }

    @Test
    @DisplayName("서로 다른 날짜에 중복된 장소는 더 이른 날짜에 한 번만 배치한다")
    void optimizeKeepsDuplicatePlaceOnEarlierDate() {
        LocalDate firstDay = LocalDate.of(2026, 7, 20);
        LocalDate secondDay = LocalDate.of(2026, 7, 21);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "경복궁", "TOUR", 99.0,
                                37.5796, 126.9770, secondDay),
                        place(1L, "경복궁", "TOUR", 80.0,
                                37.5796, 126.9770, firstDay),
                        place(2L, "서울숲", "TOUR", 90.0,
                                37.5444, 127.0374, secondDay)
                ))
                .build();

        CourseOptimizeResponse response = courseOptimizationService.optimize(request);

        assertEquals(2, response.getOptimizedPlaces().size());
        assertEquals(firstDay, response.getOptimizedPlaces().stream()
                .filter(place -> place.getPlaceId().equals(1L))
                .findFirst()
                .orElseThrow()
                .getVisitDate());
    }

    @Test
    @DisplayName("추천 점수와 경로 비용이 모두 같으면 장소 ID 순으로 결정한다")
    void optimizeUsesPlaceIdForCompleteTie() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(3L, "세 번째 장소", "CAFE", 90.0,
                                37.5665, 126.9780, visitDate),
                        place(1L, "첫 번째 장소", "CAFE", 90.0,
                                37.5665, 126.9780, visitDate),
                        place(2L, "두 번째 장소", "CAFE", 90.0,
                                37.5665, 126.9780, visitDate)
                ))
                .build();

        CourseOptimizeResponse response = courseOptimizationService.optimize(request);

        assertEquals(
                List.of(1L, 2L, 3L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(0.0, response.getTotalDistanceKm(), 0.000001);
        assertEquals(0.0, response.getTotalTravelTimeMinutes(), 0.000001);
    }

    @Test
    @DisplayName("거리 2km 초과 장소를 같은 날짜와 카테고리의 가까운 후보로 교체한다")
    void replacePlaceWhenDistanceExceedsTwoKilometers() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        PlaceCandidateDto distantPlace = place(
                2L, "먼 관광지", "TOUR", 90.0,
                37.5854, 126.9780, visitDate
        );
        distantPlace.setAlternativeCandidates(List.of(
                // 이 후보는 먼 관광지 전용이며 다른 원본 장소 교체에는 사용하지 않는다.
                place(3L, "덕수궁", "관광지", 85.0,
                        37.5658, 126.9751, null)
        ));

        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "서울시청", "TOUR", 100.0,
                                37.5665, 126.9780, visitDate),
                        // 직선거리는 약 2.1km, 예상 도보시간은 약 28분이다.
                        distantPlace
                ))
                .build();

        CourseOptimizeResponse response = courseOptimizationService.optimize(request);

        assertEquals(
                List.of(1L, 3L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertTrue(response.getTotalDistanceKm() < 2.0);
        assertTrue(response.getTotalTravelTimeMinutes() < 30.0);
        assertEquals(
                visitDate,
                response.getOptimizedPlaces().get(1).getVisitDate()
        );
        assertEquals(
                response.getOptimizedPlaces().get(1).getDistanceFromPreviousKm(),
                response.getTotalDistanceKm(),
                0.000001
        );
        assertEquals(
                response.getOptimizedPlaces().get(1).getTravelTimeFromPreviousMinutes(),
                response.getTotalTravelTimeMinutes(),
                0.000001
        );
    }

    @Test
    @DisplayName("거리가 2km 이하여도 이동시간이 30분을 초과하면 교체한다")
    void replacePlaceWhenTravelTimeExceedsThirtyMinutes() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteMatrix(
                anyList(),
                eq(TransportMode.WALKING)
        ))
                .thenAnswer(invocation -> {
                    List<PlaceCandidateDto> places = invocation.getArgument(0);
                    int size = places.size();
                    double[][] distances = new double[size][size];
                    double[][] travelTimes = new double[size][size];
                    boolean containsDistantPlace = places.stream()
                            .anyMatch(place -> place.getPlaceId().equals(2L));

                    for (int from = 0; from < size; from++) {
                        for (int to = 0; to < size; to++) {
                            if (from == to) {
                                continue;
                            }
                            distances[from][to] = containsDistantPlace ? 1.5 : 0.5;
                            travelTimes[from][to] = containsDistantPlace ? 35.0 : 10.0;
                        }
                    }
                    return new DistanceService.RouteMatrix(distances, travelTimes);
                });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        PlaceCandidateDto distantCafe = place(
                2L, "이동이 오래 걸리는 카페", "CAFE", 90.0,
                37.5670, 126.9790, visitDate
        );
        distantCafe.setAlternativeCandidates(List.of(
                place(3L, "가까운 대체 카페", "CAFE", 80.0,
                        37.5680, 126.9780, null)
        ));

        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "출발 장소", "CAFE", 100.0,
                                37.5665, 126.9780, visitDate),
                        distantCafe
                ))
                .build();

        CourseOptimizeResponse response = service.optimize(request);

        assertEquals(
                List.of(1L, 3L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(0.5, response.getTotalDistanceKm(), 0.000001);
        assertEquals(10.0, response.getTotalTravelTimeMinutes(), 0.000001);
    }

    @Test
    @DisplayName("중간 장소 교체는 앞뒤 구간의 합산 이동시간이 가장 짧은 후보를 선택한다")
    void selectReplacementUsingPreviousAndNextLegs() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteMatrix(
                anyList(),
                eq(TransportMode.WALKING)
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];

            for (int from = 0; from < size; from++) {
                for (int to = 0; to < size; to++) {
                    if (from == to) {
                        continue;
                    }
                    long fromId = places.get(from).getPlaceId();
                    long toId = places.get(to).getPlaceId();
                    long smaller = Math.min(fromId, toId);
                    long larger = Math.max(fromId, toId);
                    double travelMinutes = 15.0;
                    double distanceKm = 1.0;

                    if (smaller == 1L && larger == 2L) {
                        travelMinutes = 35.0;
                        distanceKm = 1.5;
                    } else if (smaller == 2L && larger == 3L) {
                        travelMinutes = 5.0;
                        distanceKm = 0.3;
                    } else if (smaller == 1L && larger == 3L) {
                        travelMinutes = 50.0;
                        distanceKm = 1.9;
                    } else if (smaller == 1L && larger == 4L) {
                        // 앞 구간만 보면 가장 가까운 후보이다.
                        travelMinutes = 5.0;
                        distanceKm = 0.3;
                    } else if (smaller == 3L && larger == 4L) {
                        // 하지만 다음 장소까지 포함하면 총 34분이다.
                        travelMinutes = 29.0;
                        distanceKm = 1.8;
                    } else if (smaller == 1L && larger == 5L) {
                        travelMinutes = 10.0;
                        distanceKm = 0.7;
                    } else if (smaller == 3L && larger == 5L) {
                        // 앞뒤 합계 15분으로 실제 전체 동선이 더 좋다.
                        travelMinutes = 5.0;
                        distanceKm = 0.3;
                    }
                    distances[from][to] = distanceKm;
                    travelTimes[from][to] = travelMinutes;
                }
            }
            return new DistanceService.RouteMatrix(distances, travelTimes);
        });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        PlaceCandidateDto distantPlace = place(
                2L, "먼 중간 관광지", "TOUR", 90.0,
                37.5700, 127.0100, visitDate
        );
        distantPlace.setAlternativeCandidates(List.of(
                place(4L, "앞 구간만 가까운 후보", "TOUR", 95.0,
                        37.5701, 127.0101, null),
                place(5L, "전체 동선이 짧은 후보", "TOUR", 80.0,
                        37.5702, 127.0102, null)
        ));

        CourseOptimizeResponse response = service.optimize(
                CourseOptimizeRequest.builder()
                        .transportMode(TransportMode.WALKING)
                        .placeCandidates(List.of(
                                place(1L, "출발지", "TOUR", 100.0,
                                        37.5600, 127.0000, visitDate),
                                distantPlace,
                                place(3L, "다음 장소", "TOUR", 70.0,
                                        37.5800, 127.0200, visitDate)
                        ))
                        .build()
        );

        assertEquals(
                List.of(1L, 5L, 3L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(15.0, response.getTotalTravelTimeMinutes(), 0.000001);
    }

    @Test
    @DisplayName("조건을 만족하는 대체 후보가 없으면 원래 장소와 경로를 유지한다")
    void keepOriginalPlaceWhenNoUsableAlternativeExists() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        PlaceCandidateDto distantPlace = place(
                2L, "서울숲", "TOUR", 90.0,
                37.5444, 127.0374, visitDate
        );
        distantPlace.setAlternativeCandidates(List.of(
                // 카테고리가 다르므로 관광지 교체 후보로 사용할 수 없다.
                place(3L, "가까운 카페", "CAFE", 95.0,
                        37.5658, 126.9751, null)
        ));

        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "서울시청", "TOUR", 100.0,
                                37.5665, 126.9780, visitDate),
                        distantPlace
                ))
                .build();

        CourseOptimizeResponse response = courseOptimizationService.optimize(request);

        assertEquals(
                List.of(1L, 2L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertTrue(response.getTotalDistanceKm() > 2.0);
    }

    @Test
    @DisplayName("현재 코스와 같은 장소 ID의 대체 후보는 중복 삽입하지 않는다")
    void ignoreAlternativeAlreadyIncludedInCourse() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        PlaceCandidateDto distantPlace = place(
                2L, "서울숲", "TOUR", 90.0,
                37.5444, 127.0374, visitDate
        );
        distantPlace.setAlternativeCandidates(List.of(
                // 장소 ID 2는 이미 코스에 있으므로 좌표가 가까워도 대체 후보로 쓰지 않는다.
                place(2L, "중복 대체 후보", "TOUR", 95.0,
                        37.5658, 126.9751, null)
        ));

        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "서울시청", "TOUR", 100.0,
                                37.5665, 126.9780, visitDate),
                        distantPlace
                ))
                .build();

        CourseOptimizeResponse response = courseOptimizationService.optimize(request);

        assertEquals(
                List.of(1L, 2L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(2, response.getOptimizedPlaces().size());
        assertTrue(response.getTotalDistanceKm() > 2.0);
    }

    @Test
    @DisplayName("다일 코스는 날짜별 먼 장소를 각각 교체하고 방문 순서를 날짜마다 다시 계산한다")
    void replaceDistantPlacesForMultipleDays() {
        LocalDate firstDay = LocalDate.of(2026, 7, 20);
        LocalDate secondDay = LocalDate.of(2026, 7, 21);
        PlaceCandidateDto firstDayDistantPlace = place(
                2L, "첫날 먼 장소", "TOUR", 90.0,
                37.5854, 126.9780, firstDay
        );
        firstDayDistantPlace.setAlternativeCandidates(List.of(
                place(5L, "첫날 가까운 관광지", "TOUR", 85.0,
                        37.5658, 126.9751, null)
        ));

        PlaceCandidateDto secondDayDistantPlace = place(
                4L, "둘째날 먼 카페", "CAFE", 90.0,
                37.5761, 126.9254, secondDay
        );
        secondDayDistantPlace.setAlternativeCandidates(List.of(
                place(6L, "둘째날 가까운 카페", "CAFE", 85.0,
                        37.5580, 126.9270, null)
        ));

        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "첫날 출발", "TOUR", 100.0,
                                37.5665, 126.9780, firstDay),
                        firstDayDistantPlace,
                        place(3L, "둘째날 출발", "CAFE", 100.0,
                                37.5572, 126.9254, secondDay),
                        secondDayDistantPlace
                ))
                .build();

        CourseOptimizeResponse response = courseOptimizationService.optimize(request);

        assertEquals(
                List.of(1L, 5L, 3L, 6L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(
                List.of(1, 2, 1, 2),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getVisitOrder)
                        .toList()
        );
        assertEquals(firstDay, response.getOptimizedPlaces().get(0).getVisitDate());
        assertEquals(secondDay, response.getOptimizedPlaces().get(2).getVisitDate());
        assertEquals(
                0.0,
                response.getOptimizedPlaces().get(2).getDistanceFromPreviousKm(),
                0.000001
        );
        assertEquals(
                0.0,
                response.getOptimizedPlaces().get(2).getTravelTimeFromPreviousMinutes(),
                0.000001
        );
    }

    @Test
    @DisplayName("자동차는 40분을 넘어도 시간 제한으로 장소를 교체하지 않는다")
    void applyDifferentReplacementThresholdsByTransportMode() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteMatrix(
                anyList(),
                any(TransportMode.class)
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            boolean containsOriginal = places.stream()
                    .anyMatch(place -> place.getPlaceId().equals(2L));

            for (int from = 0; from < size; from++) {
                for (int to = 0; to < size; to++) {
                    if (from == to) {
                        continue;
                    }
                    distances[from][to] = containsOriginal ? 5.0 : 0.5;
                    travelTimes[from][to] = containsOriginal ? 55.0 : 10.0;
                }
            }
            return new DistanceService.RouteMatrix(distances, travelTimes);
        });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        PlaceCandidateDto distantCafe = place(
                2L, "이동 구간 카페", "CAFE", 90.0,
                37.60, 127.00, visitDate
        );
        distantCafe.setAlternativeCandidates(List.of(place(
                3L, "가까운 대체 카페", "CAFE", 80.0,
                37.57, 126.98, null
        )));
        List<PlaceCandidateDto> candidates = List.of(
                place(1L, "출발지", "TOUR", 100.0,
                        37.5665, 126.9780, visitDate),
                distantCafe
        );

        CourseOptimizeResponse walking = service.optimize(
                CourseOptimizeRequest.builder()
                        .transportMode(TransportMode.WALKING)
                        .placeCandidates(candidates)
                        .build()
        );
        CourseOptimizeResponse driving = service.optimize(
                CourseOptimizeRequest.builder()
                        .transportMode(TransportMode.DRIVING)
                        .placeCandidates(candidates)
                        .build()
        );

        assertEquals(
                List.of(1L, 3L),
                walking.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(
                List.of(1L, 2L),
                driving.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
    }

    @Test
    @DisplayName("대중교통 40분 초과 구간은 같은 카테고리 대체 후보로 교체한다")
    void replacesPublicTransitLegOverFortyMinutes() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateCandidatePoolMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT)
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            boolean containsDistantPlace = places.stream()
                    .anyMatch(place -> place.getPlaceId().equals(2L));
            double travelMinutes = containsDistantPlace ? 40.1 : 20.0;
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            for (int from = 0; from < size; from++) {
                for (int to = 0; to < size; to++) {
                    if (from == to) {
                        continue;
                    }
                    distances[from][to] = 5.0;
                    travelTimes[from][to] = travelMinutes;
                }
            }
            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    true
            );
        });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);
        PlaceCandidateDto distantCafe = place(
                2L, "40분 초과 카페", "CAFE", 90.0,
                37.60, 127.05, visitDate
        );
        distantCafe.setAlternativeCandidates(List.of(place(
                3L, "가까운 대체 카페", "CAFE", 85.0,
                37.57, 127.00, null
        )));

        CourseOptimizeResponse response = service.optimizeForRecommendation(
                CourseOptimizeRequest.builder()
                        .transportMode(TransportMode.PUBLIC_TRANSIT)
                        .placeCandidates(List.of(
                                place(1L, "출발지", "TOUR", 100.0,
                                        37.5665, 126.9780, visitDate),
                                distantCafe
                        ))
                        .build(),
                Map.of()
        );

        assertEquals(
                List.of(1L, 3L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
    }

    @Test
    @DisplayName("대중교통 정확히 40분인 구간은 유지한다")
    void keepsPublicTransitLegAtFortyMinutes() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateCandidatePoolMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT)
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            for (int from = 0; from < size; from++) {
                for (int to = 0; to < size; to++) {
                    if (from == to) {
                        continue;
                    }
                    distances[from][to] = 5.0;
                    travelTimes[from][to] = 40.0;
                }
            }
            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    true
            );
        });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);
        PlaceCandidateDto cafe = place(
                2L, "40분 카페", "CAFE", 90.0,
                37.60, 127.05, visitDate
        );
        cafe.setAlternativeCandidates(List.of(place(
                3L, "대체 카페", "CAFE", 85.0,
                37.57, 127.00, null
        )));

        CourseOptimizeResponse response = service.optimizeForRecommendation(
                CourseOptimizeRequest.builder()
                        .transportMode(TransportMode.PUBLIC_TRANSIT)
                        .placeCandidates(List.of(
                                place(1L, "출발지", "TOUR", 100.0,
                                        37.5665, 126.9780, visitDate),
                                cafe
                        ))
                        .build(),
                Map.of()
        );

        assertEquals(
                List.of(1L, 2L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
    }

    @Test
    @DisplayName("추천 옵션 생성 중에는 ODsay 최종 구간 조회를 호출하지 않는다")
    void recommendationOptimizationUsesOnlyEstimatedTransitMatrix() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        double[][] distances = {
                {0.0, 1.0, 2.0},
                {1.0, 0.0, 1.0},
                {2.0, 1.0, 0.0}
        };
        double[][] travelTimes = {
                {0.0, 10.0, 20.0},
                {10.0, 0.0, 10.0},
                {20.0, 10.0, 0.0}
        };
        when(mockedDistanceService.calculateCandidatePoolMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT)
        )).thenReturn(new DistanceService.RouteMatrix(
                distances,
                travelTimes,
                true
        ));
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .placeCandidates(List.of(
                        place(1L, "서울시청", "TOUR", 100.0,
                                37.5665, 126.9780, visitDate),
                        place(2L, "덕수궁", "TOUR", 90.0,
                                37.5658, 126.9751, visitDate),
                        place(3L, "경복궁", "TOUR", 80.0,
                                37.5796, 126.9770, visitDate)
                ))
                .build();

        CourseOptimizeResponse response =
                service.optimizeForRecommendation(request, Map.of());

        assertTrue(response.getEstimatedTravelTimes());
        verify(mockedDistanceService, never()).calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        );
    }

    @Test
    @DisplayName("도보 추천의 대체 후보 판단도 외부 경로 행렬을 호출하지 않는다")
    void recommendationAlternativeSelectionUsesEstimatedWalkingMatrix() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        double[][] distances = {
                {0.0, 3.0},
                {3.0, 0.0}
        };
        double[][] travelTimes = {
                {0.0, 40.0},
                {40.0, 0.0}
        };
        when(mockedDistanceService.calculateCandidatePoolMatrix(
                anyList(),
                eq(TransportMode.WALKING)
        )).thenReturn(new DistanceService.RouteMatrix(
                distances,
                travelTimes,
                true
        ));
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        PlaceCandidateDto distant = place(
                2L,
                "먼 관광지",
                "TOUR",
                90.0,
                37.58,
                127.05,
                visitDate
        );
        distant.setAlternativeCandidates(List.of(place(
                3L,
                "대체 관광지",
                "TOUR",
                85.0,
                37.57,
                127.01,
                visitDate
        )));
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "출발지", "TOUR", 100.0,
                                37.56, 127.00, visitDate),
                        distant
                ))
                .build();

        CourseOptimizeResponse response =
                service.optimizeForRecommendation(request, Map.of());

        assertTrue(response.getEstimatedTravelTimes());
        verify(mockedDistanceService, never()).calculateRouteMatrix(
                anyList(),
                eq(TransportMode.WALKING)
        );
    }

    @Test
    @DisplayName("ODsay 실패 추정값이 40분을 넘어도 장소를 제거하거나 재조회하지 않는다")
    void doesNotRepairEstimatedPublicTransitViolation() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            boolean[][] estimated = new boolean[size][size];
            TransitPathType[][] pathTypes = new TransitPathType[size][size];
            for (int index = 1; index < size; index++) {
                distances[index - 1][index] = 8.0;
                travelTimes[index - 1][index] = 55.0;
                estimated[index - 1][index] = true;
            }
            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    estimated,
                    pathTypes
            );
        });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .placeCandidates(List.of(
                        place(1L, "출발지", "TOUR", 100.0,
                                37.56, 126.97, visitDate),
                        place(2L, "식당", "RESTAURANT", 95.0,
                                37.57, 126.98, visitDate),
                        place(3L, "카페", "CAFE", 90.0,
                                37.58, 126.99, visitDate),
                        place(4L, "마지막 관광지", "TOUR", 85.0,
                                37.59, 127.00, visitDate)
                ))
                .build();

        CourseOptimizeResponse response =
                service.resolveFixedRouteDetails(request);

        assertEquals(
                List.of(1L, 2L, 3L, 4L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertTrue(response.getEstimatedTravelTimes());
        verify(mockedDistanceService, times(1)).calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        );
    }

    @Test
    @DisplayName("실제 대중교통 40분 초과 장소는 삭제 전에 같은 카테고리 후보로 교체한다")
    void replacesActualPublicTransitViolationBeforeRemovingPlace() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            boolean[][] estimated = new boolean[size][size];
            TransitPathType[][] pathTypes = new TransitPathType[size][size];
            List<Long> placeIds = places.stream()
                    .map(PlaceCandidateDto::getPlaceId)
                    .toList();

            for (int index = 1; index < size; index++) {
                distances[index - 1][index] = 2.0;
                travelTimes[index - 1][index] = 20.0;
                pathTypes[index - 1][index] = TransitPathType.SUBWAY;
            }
            if (placeIds.equals(List.of(1L, 2L, 3L))) {
                travelTimes[0][1] = 51.0;
            }
            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    estimated,
                    pathTypes
            );
        });
        when(mockedDistanceService.calculateCandidatePoolMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT)
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            for (int from = 0; from < size; from++) {
                for (int to = 0; to < size; to++) {
                    if (from == to) continue;
                    distances[from][to] = 2.0;
                    travelTimes[from][to] = 20.0;
                }
            }
            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    true
            );
        });

        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);
        PlaceCandidateDto distantTour = place(
                2L, "먼 관광지", "TOUR", 90.0,
                37.65, 127.10, visitDate
        );
        distantTour.setAlternativeCandidates(List.of(place(
                4L, "가까운 대체 관광지", "TOUR", 85.0,
                37.57, 126.99, null
        )));
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .placeCandidates(List.of(
                        place(1L, "출발 식당", "RESTAURANT", 100.0,
                                37.56, 126.97, visitDate),
                        distantTour,
                        place(3L, "숙소", "HOTEL", 80.0,
                                37.55, 126.96, visitDate)
                ))
                .build();

        CourseOptimizeResponse response =
                service.resolveFixedRouteDetails(request);

        assertEquals(
                List.of(1L, 4L, 3L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertTrue(response.getOptimizedPlaces().stream()
                .skip(1)
                .allMatch(place ->
                        place.getTravelTimeFromPreviousMinutes() <= 40.0
                ));
        verify(mockedDistanceService, times(2)).calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        );
    }

    @Test
    @DisplayName("대중교통 40분 제한 때문에 최소 일반 장소 수를 깨야 하면 경로 보정을 실패시킨다")
    void rejectsPublicTransitRepairBelowMinimumOrdinaryPlaces() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            boolean[][] estimated = new boolean[size][size];
            TransitPathType[][] pathTypes = new TransitPathType[size][size];
            for (int index = 1; index < size; index++) {
                distances[index - 1][index] = 2.0;
                travelTimes[index - 1][index] = 15.0;
                pathTypes[index - 1][index] = TransitPathType.SUBWAY;
            }
            if (places.stream().anyMatch(place ->
                    place.getPlaceId().equals(2L))) {
                travelTimes[0][1] = 51.0;
            }
            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    estimated,
                    pathTypes
            );
        });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .placeCandidates(List.of(
                        place(1L, "출발지", "RESTAURANT", 100.0,
                                37.56, 126.97, visitDate),
                        place(2L, "51분 관광지", "TOUR", 90.0,
                                37.65, 127.10, visitDate),
                        place(3L, "숙소", "HOTEL", 80.0,
                                37.55, 126.96, visitDate)
                ))
                .build();

        PublicTransitMinimumPlaceException exception = assertThrows(
                PublicTransitMinimumPlaceException.class,
                () -> service.resolveFixedRouteDetails(request)
        );

        assertTrue(exception.getMessage().contains("최소 장소 수"));
    }

    @Test
    @DisplayName("제한 해제 값이 와도 실제 대중교통 40분 초과 구간을 반환하지 않는다")
    void doesNotAllowPublicTransitLimitBypass() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            boolean[][] estimated = new boolean[size][size];
            TransitPathType[][] pathTypes = new TransitPathType[size][size];
            for (int index = 1; index < size; index++) {
                distances[index - 1][index] = 2.0;
                travelTimes[index - 1][index] = 15.0;
                pathTypes[index - 1][index] = TransitPathType.SUBWAY;
            }
            if (places.stream().anyMatch(place ->
                    place.getPlaceId().equals(2L))) {
                travelTimes[0][1] = 51.0;
            }
            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    estimated,
                    pathTypes
            );
        });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .travelCode("ATMSP")
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .enforcePublicTransitLimit(false)
                .placeCandidates(List.of(
                        place(1L, "출발지", "RESTAURANT", 100.0,
                                37.56, 126.97, visitDate),
                        place(2L, "51분 관광지", "TOUR", 90.0,
                                37.65, 127.10, visitDate),
                        place(3L, "카페", "CAFE", 85.0,
                                37.58, 126.99, visitDate),
                        place(4L, "마지막 관광지", "TOUR", 80.0,
                                37.59, 127.00, visitDate)
                ))
                .build();

        CourseOptimizeResponse response =
                service.resolveFixedRouteDetails(request);

        assertEquals(
                List.of(1L, 3L, 4L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertTrue(response.getOptimizedPlaces().stream()
                .skip(1)
                .allMatch(place ->
                        place.getTravelTimeFromPreviousMinutes() <= 40.0
                ));
        assertFalse(response.getEstimatedTravelTimes());
        verify(mockedDistanceService, times(2)).calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        );
    }

    @Test
    @DisplayName("대중교통 후보 확대 단계에서는 장소 수를 먼저 줄이지 않는다")
    void defersPublicTransitPlaceReductionUntilFinalTier() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            boolean[][] estimated = new boolean[size][size];
            TransitPathType[][] pathTypes = new TransitPathType[size][size];
            for (int index = 1; index < size; index++) {
                distances[index - 1][index] = 2.0;
                travelTimes[index - 1][index] = 15.0;
                pathTypes[index - 1][index] = TransitPathType.SUBWAY;
            }
            travelTimes[0][1] = 51.0;
            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    estimated,
                    pathTypes
            );
        });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .allowPublicTransitPlaceReduction(false)
                .placeCandidates(List.of(
                        place(1L, "출발지", "RESTAURANT", 100.0,
                                37.56, 126.97, visitDate),
                        place(2L, "먼 관광지", "TOUR", 90.0,
                                37.65, 127.10, visitDate),
                        place(3L, "카페", "CAFE", 85.0,
                                37.58, 126.99, visitDate),
                        place(4L, "마지막 관광지", "TOUR", 80.0,
                                37.59, 127.00, visitDate)
                ))
                .build();

        assertThrows(
                PublicTransitMinimumPlaceException.class,
                () -> service.resolveFixedRouteDetails(request)
        );
        verify(mockedDistanceService, times(1)).calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        );
    }

    @Test
    @DisplayName("실제 대중교통 40분 초과 구간은 최소 3곳을 남기고 먼 장소를 제외한다")
    void removesActualPublicTransitLegOverFortyMinutes() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            boolean[][] estimated = new boolean[size][size];
            TransitPathType[][] pathTypes = new TransitPathType[size][size];

            for (int index = 1; index < size; index++) {
                distances[index - 1][index] = 2.0;
                travelTimes[index - 1][index] = 10.0;
                pathTypes[index - 1][index] = TransitPathType.SUBWAY;
            }
            if (places.stream().anyMatch(place -> place.getPlaceId().equals(3L))) {
                travelTimes[1][2] = 50.0;
            } else if (size >= 3) {
                travelTimes[1][2] = 15.0;
            }
            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    estimated,
                    pathTypes
            );
        });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .placeCandidates(List.of(
                        place(1L, "출발지", "TOUR", 100.0,
                                37.56, 126.97, visitDate),
                        place(2L, "두 번째 장소", "RESTAURANT", 95.0,
                                37.57, 126.98, visitDate),
                        place(3L, "50분 거리 장소", "CAFE", 90.0,
                                37.65, 127.10, visitDate),
                        place(4L, "마지막 장소", "TOUR", 85.0,
                                37.58, 126.99, visitDate)
                ))
                .build();

        CourseOptimizeResponse response = service.resolveFixedRouteDetails(request);

        assertEquals(
                List.of(1L, 2L, 4L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertTrue(response.getOptimizedPlaces().stream()
                .skip(1)
                .allMatch(place -> place.getTravelTimeFromPreviousMinutes() <= 40.0));
        verify(mockedDistanceService, times(2)).calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        );
    }

    @Test
    @DisplayName("실제 대중교통 30분 초과 구간은 DAY마다 하나만 유지한다")
    void keepsOnlyOneLongPublicTransitLegPerDay() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            boolean[][] estimated = new boolean[size][size];
            TransitPathType[][] pathTypes = new TransitPathType[size][size];

            for (int index = 1; index < size; index++) {
                distances[index - 1][index] = 2.0;
                travelTimes[index - 1][index] = 20.0;
                pathTypes[index - 1][index] = TransitPathType.SUBWAY;
            }

            List<Long> placeIds = places.stream()
                    .map(PlaceCandidateDto::getPlaceId)
                    .toList();
            if (placeIds.equals(List.of(1L, 2L, 3L, 4L, 5L))) {
                travelTimes[0][1] = 11.0;
                travelTimes[1][2] = 39.0;
                travelTimes[2][3] = 27.0;
                travelTimes[3][4] = 37.0;
            } else if (placeIds.equals(List.of(1L, 2L, 3L, 5L))) {
                travelTimes[0][1] = 11.0;
                travelTimes[1][2] = 39.0;
                travelTimes[2][3] = 25.0;
            }

            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    estimated,
                    pathTypes
            );
        });
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .placeCandidates(List.of(
                        place(1L, "식당", "RESTAURANT", 100.0,
                                37.56, 126.97, visitDate),
                        place(2L, "관광지", "TOUR", 95.0,
                                37.57, 126.98, visitDate),
                        place(3L, "카페", "CAFE", 90.0,
                                37.58, 126.99, visitDate),
                        place(4L, "먼 공원", "TOUR", 85.0,
                                37.65, 127.10, visitDate),
                        place(5L, "숙소", "HOTEL", 80.0,
                                37.55, 126.96, visitDate)
                ))
                .build();

        CourseOptimizeResponse response = service.resolveFixedRouteDetails(request);

        assertEquals(
                List.of(1L, 2L, 3L, 5L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(
                1L,
                response.getOptimizedPlaces().stream()
                        .skip(1)
                        .filter(place ->
                                place.getTravelTimeFromPreviousMinutes() > 30.0
                        )
                        .count()
        );
        verify(mockedDistanceService, times(2)).calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        );
    }

    @Test
    @DisplayName("실제 경로 보정 후에도 식당 두 곳을 연속 배치하지 않는다")
    void separatesConsecutiveRestaurantsAfterActualRouteRepair() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                anyList()
        )).thenAnswer(invocation -> {
            List<PlaceCandidateDto> places = invocation.getArgument(0);
            int size = places.size();
            double[][] distances = new double[size][size];
            double[][] travelTimes = new double[size][size];
            boolean[][] estimated = new boolean[size][size];
            TransitPathType[][] pathTypes = new TransitPathType[size][size];

            for (int index = 1; index < size; index++) {
                distances[index - 1][index] = 1.0;
                travelTimes[index - 1][index] = 10.0;
                pathTypes[index - 1][index] = TransitPathType.SUBWAY;
            }
            return new DistanceService.RouteMatrix(
                    distances,
                    travelTimes,
                    estimated,
                    pathTypes
            );
        });

        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .placeCandidates(List.of(
                        place(1L, "관광지", "TOUR", 100.0,
                                37.56, 126.97, visitDate),
                        place(2L, "카페", "CAFE", 95.0,
                                37.57, 126.98, visitDate),
                        place(3L, "첫 식당", "RESTAURANT", 90.0,
                                37.58, 126.99, visitDate),
                        place(4L, "둘째 식당", "RESTAURANT", 85.0,
                                37.59, 127.00, visitDate)
                ))
                .build();

        CourseOptimizeResponse response =
                service.resolveFixedRouteDetails(request);

        assertEquals(
                List.of(1L, 3L, 2L, 4L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        for (int index = 1;
                index < response.getOptimizedPlaces().size();
                index++) {
            String previousCategory = response.getOptimizedPlaces()
                    .get(index - 1)
                    .getCategory();
            String currentCategory = response.getOptimizedPlaces()
                    .get(index)
                    .getCategory();
            assertFalse(
                    "RESTAURANT".equals(previousCategory)
                            && "RESTAURANT".equals(currentCategory)
            );
        }
    }

    @Test
    @DisplayName("상세 경로 조회는 프런트 방문 순서를 유지한다")
    void resolveFixedRouteDetailsPreservesRequestedOrder() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        double[][] distances = {
                {0.0, 1.2, 0.0},
                {0.0, 0.0, 2.3},
                {0.0, 0.0, 0.0}
        };
        double[][] travelTimes = {
                {0.0, 12.0, 0.0},
                {0.0, 0.0, 23.0},
                {0.0, 0.0, 0.0}
        };
        boolean[][] estimated = new boolean[3][3];
        TransitPathType[][] pathTypes = new TransitPathType[3][3];
        pathTypes[0][1] = TransitPathType.BUS;
        pathTypes[1][2] = TransitPathType.SUBWAY;
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.PUBLIC_TRANSIT),
                eq(List.of(0, 1, 2))
        )).thenReturn(new DistanceService.RouteMatrix(
                distances,
                travelTimes,
                estimated,
                pathTypes
        ));
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .placeCandidates(List.of(
                        place(3L, "경복궁", "TOUR", 80.0,
                                37.5796, 126.9770, visitDate),
                        place(1L, "서울시청", "TOUR", 100.0,
                                37.5665, 126.9780, visitDate),
                        place(2L, "덕수궁", "TOUR", 90.0,
                                37.5658, 126.9751, visitDate)
                ))
                .build();

        CourseOptimizeResponse response =
                service.resolveFixedRouteDetails(request);

        assertEquals(
                List.of(3L, 1L, 2L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(
                TransitPathType.BUS,
                response.getOptimizedPlaces().get(1).getTransitPathType()
        );
        assertEquals(
                TransitPathType.SUBWAY,
                response.getOptimizedPlaces().get(2).getTransitPathType()
        );
        assertEquals(3.5, response.getTotalDistanceKm(), 0.000001);
        assertFalse(response.getEstimatedTravelTimes());
    }

    @Test
    @DisplayName("상세 경로 조회는 다른 날짜에 재사용된 같은 장소를 제거하지 않는다")
    void resolveFixedRouteDetailsPreservesSamePlaceAcrossDifferentDates() {
        DistanceService mockedDistanceService = mock(DistanceService.class);
        double[][] distances = {
                {0.0, 1.0},
                {0.0, 0.0}
        };
        double[][] travelTimes = {
                {0.0, 10.0},
                {0.0, 0.0}
        };
        boolean[][] estimated = new boolean[2][2];
        TransitPathType[][] pathTypes = new TransitPathType[2][2];
        when(mockedDistanceService.calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.WALKING),
                eq(List.of(0, 1))
        )).thenReturn(new DistanceService.RouteMatrix(
                distances,
                travelTimes,
                estimated,
                pathTypes
        ));
        CourseOptimizationService service = new CourseOptimizationService(
                mockedDistanceService,
                new VisitDurationService()
        );
        LocalDate firstDay = LocalDate.of(2026, 7, 24);
        LocalDate secondDay = LocalDate.of(2026, 7, 25);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(
                        place(1L, "첫째 날 출발지", "TOUR", 100.0,
                                37.5665, 126.9780, firstDay),
                        place(2L, "재사용 장소", "CAFE", 90.0,
                                37.5670, 126.9790, firstDay),
                        place(3L, "둘째 날 출발지", "TOUR", 95.0,
                                37.5700, 126.9800, secondDay),
                        place(2L, "재사용 장소", "CAFE", 90.0,
                                37.5670, 126.9790, secondDay)
                ))
                .build();

        CourseOptimizeResponse response =
                service.resolveFixedRouteDetails(request);

        assertEquals(4, response.getOptimizedPlaces().size());
        assertEquals(
                List.of(1L, 2L, 3L, 2L),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getPlaceId)
                        .toList()
        );
        assertEquals(
                List.of(firstDay, firstDay, secondDay, secondDay),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getVisitDate)
                        .toList()
        );
        assertEquals(
                List.of(1, 2, 1, 2),
                response.getOptimizedPlaces().stream()
                        .map(OptimizedPlaceDto::getVisitOrder)
                        .toList()
        );
        verify(mockedDistanceService, times(2)).calculateRouteLegMatrix(
                anyList(),
                eq(TransportMode.WALKING),
                eq(List.of(0, 1))
        );
    }

    @Test
    @DisplayName("방문 날짜가 없는 장소 후보는 예외가 발생한다")
    void optimizeRejectsCandidateWithoutVisitDate() {
        PlaceCandidateDto invalidCandidate = PlaceCandidateDto.builder()
                .placeId(1L)
                .placeName("날짜 없는 장소")
                .category("관광지")
                .recommendationScore(90.0)
                .latitude(37.5665)
                .longitude(126.9780)
                .build();

        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .transportMode(TransportMode.WALKING)
                .placeCandidates(List.of(invalidCandidate))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> courseOptimizationService.optimize(request)
        );
    }

    /** 테스트마다 반복되는 장소 후보 생성을 한곳에 모은다. */
    private PlaceCandidateDto place(
            Long placeId,
            String placeName,
            String category,
            Double recommendationScore,
            Double latitude,
            Double longitude,
            LocalDate visitDate
    ) {
        return PlaceCandidateDto.builder()
                .placeId(placeId)
                .placeName(placeName)
                .category(category)
                .recommendationScore(recommendationScore)
                .latitude(latitude)
                .longitude(longitude)
                .visitDate(visitDate)
                .build();
    }
}
