package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 날짜 분리, 중복 제거, 경로 선택 우선순위와 입력값 검증을 확인한다. */
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
                List.of(1L, 2L, 3L, 4L, 5L, 6L),
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
    @DisplayName("장소 후보가 비어 있으면 빈 최적화 결과를 반환한다")
    void optimizeReturnsEmptyResponseForEmptyCandidates() {
        CourseOptimizeResponse response = courseOptimizationService.optimize(
                CourseOptimizeRequest.builder().build()
        );

        assertTrue(response.getOptimizedPlaces().isEmpty());
        assertEquals(0.0, response.getTotalDistanceKm(), 0.000001);
        assertEquals(0.0, response.getTotalTravelTimeMinutes(), 0.000001);
        assertEquals(0, response.getTotalVisitTimeMinutes());
        assertEquals(0.0, response.getTotalCourseTimeMinutes(), 0.000001);
    }

    @Test
    @DisplayName("장소가 한 개면 이동거리와 이동시간 없이 방문 순서 1로 반환한다")
    void optimizeSingleCandidate() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
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
                .placeCandidates(List.of(invalidCandidate))
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
