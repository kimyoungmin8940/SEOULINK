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

class CourseOptimizationServiceTest {

    private CourseOptimizationService courseOptimizationService;

    @BeforeEach
    void setUp() {
        courseOptimizationService = new CourseOptimizationService(new DistanceService());
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

        double distanceSum = result.stream()
                .mapToDouble(OptimizedPlaceDto::getDistanceFromPreviousKm)
                .sum();
        double travelTimeSum = result.stream()
                .mapToDouble(OptimizedPlaceDto::getTravelTimeFromPreviousMinutes)
                .sum();

        assertEquals(distanceSum, response.getTotalDistanceKm(), 0.000001);
        assertEquals(travelTimeSum, response.getTotalTravelTimeMinutes(), 0.000001);
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
                .expectedVisitMinutes(60)
                .build();

        CourseOptimizeRequest request = CourseOptimizeRequest.builder()
                .placeCandidates(List.of(invalidCandidate))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> courseOptimizationService.optimize(request)
        );
    }

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
                .expectedVisitMinutes(60)
                .build();
    }
}
