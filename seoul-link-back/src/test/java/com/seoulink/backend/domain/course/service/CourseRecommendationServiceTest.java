package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.DailyPlanRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 확정 날짜별 JSON이 최적화 입력으로 변환되는 흐름을 검증한다. */
class CourseRecommendationServiceTest {

    private CourseOptimizationService courseOptimizationService;
    private CourseRecommendationService courseRecommendationService;

    @BeforeEach
    void setUp() {
        courseOptimizationService = mock(CourseOptimizationService.class);
        courseRecommendationService = new CourseRecommendationService(
                courseOptimizationService
        );
    }

    @Test
    @DisplayName("dailyPlans의 날짜를 장소와 전용 대체 후보에 적용해 최적화한다")
    void recommendWithFinalRequestContract() {
        LocalDate firstDate = LocalDate.of(2026, 7, 20);
        LocalDate secondDate = LocalDate.of(2026, 7, 21);
        PlaceCandidateDto alternative = PlaceCandidateDto.builder()
                .placeId(20L)
                .placeName("창덕궁")
                .category("TOUR")
                .recommendationScore(88.0)
                .latitude(37.5794)
                .longitude(126.9910)
                .themePalaceCultureYn("Y")
                .build();
        PlaceCandidateDto palace = PlaceCandidateDto.builder()
                .placeId(10L)
                .placeName("경복궁")
                .category("TOUR")
                .recommendationScore(92.0)
                .latitude(37.5796)
                .longitude(126.9770)
                .themePalaceCultureYn("Y")
                .alternativeCandidates(List.of(alternative))
                .build();
        PlaceCandidateDto forest = PlaceCandidateDto.builder()
                .placeId(30L)
                .placeName("서울숲")
                .category("TOUR")
                .recommendationScore(94.0)
                .latitude(37.5444)
                .longitude(127.0374)
                .themeNatureHangangYn("Y")
                .themeDateYn("Y")
                .build();

        when(courseOptimizationService.optimize(any(CourseOptimizeRequest.class)))
                .thenReturn(CourseOptimizeResponse.builder()
                        .optimizedPlaces(List.of(
                                optimizedPlace(10L, "경복궁", firstDate, 1, "Y", null),
                                optimizedPlace(30L, "서울숲", secondDate, 1, null, "Y")
                        ))
                        .totalDistanceKm(0.0)
                        .totalTravelTimeMinutes(0.0)
                        .totalVisitTimeMinutes(180)
                        .totalCourseTimeMinutes(180.0)
                        .build());

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .dailyStartTime(LocalTime.of(10, 0))
                .dailyPlans(List.of(
                        DailyPlanRequest.builder()
                                .visitDate(firstDate)
                                .placeCandidates(List.of(palace))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(secondDate)
                                .placeCandidates(List.of(forest))
                                .build()
                ))
                .build();

        CourseRecommendResponse response =
                courseRecommendationService.recommend(request);

        ArgumentCaptor<CourseOptimizeRequest> captor =
                ArgumentCaptor.forClass(CourseOptimizeRequest.class);
        verify(courseOptimizationService).optimize(captor.capture());
        List<PlaceCandidateDto> flattened = captor.getValue().getPlaceCandidates();

        assertEquals(2, flattened.size());
        assertEquals(firstDate, flattened.get(0).getVisitDate());
        assertEquals(secondDate, flattened.get(1).getVisitDate());
        assertEquals(1, flattened.get(0).getAlternativeCandidates().size());
        assertEquals(
                firstDate,
                flattened.get(0).getAlternativeCandidates().get(0).getVisitDate()
        );
        assertEquals(20L, flattened.get(0).getAlternativeCandidates().get(0).getPlaceId());
        assertNotSame(palace, flattened.get(0));
        assertEquals(101L, response.getResultId());
        assertEquals(LocalTime.of(10, 0), response.getDailyStartTime());
        assertEquals(2, response.getPlaceCount());
        assertEquals(2, response.getDayCount());
        assertEquals("Y", response.getDays().get(0).getPlaces().get(0)
                .getThemePalaceCultureYn());
        assertEquals("10:00", response.getDays().get(0).getPlaces().get(0)
                .getVisitTime());
        assertEquals("Y", response.getDays().get(1).getPlaces().get(0)
                .getThemeNatureHangangYn());
    }

    @Test
    @DisplayName("동일한 방문 날짜가 두 번 들어오면 요청을 거부한다")
    void rejectDuplicateVisitDates() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        DailyPlanRequest plan = DailyPlanRequest.builder()
                .visitDate(date)
                .placeCandidates(List.of(validCandidate(1L)))
                .build();
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .dailyStartTime(LocalTime.of(10, 0))
                .dailyPlans(List.of(plan, plan))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> courseRecommendationService.recommend(request)
        );
    }

    @Test
    @DisplayName("일정 시작 시각이 없으면 요청을 거부한다")
    void rejectMissingDailyStartTime() {
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 20))
                        .placeCandidates(List.of(validCandidate(1L)))
                        .build()))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> courseRecommendationService.recommend(request)
        );
    }

    private PlaceCandidateDto validCandidate(Long placeId) {
        return PlaceCandidateDto.builder()
                .placeId(placeId)
                .placeName("장소 " + placeId)
                .category("TOUR")
                .recommendationScore(90.0)
                .latitude(37.5)
                .longitude(127.0)
                .build();
    }

    private OptimizedPlaceDto optimizedPlace(
            Long placeId,
            String placeName,
            LocalDate visitDate,
            int visitOrder,
            String palaceTheme,
            String natureTheme
    ) {
        return OptimizedPlaceDto.builder()
                .placeId(placeId)
                .placeName(placeName)
                .category("TOUR")
                .recommendationScore(90.0)
                .latitude(37.5)
                .longitude(127.0)
                .visitDate(visitDate)
                .themePalaceCultureYn(palaceTheme)
                .themeNatureHangangYn(natureTheme)
                .expectedVisitMinutes(90)
                .visitOrder(visitOrder)
                .distanceFromPreviousKm(0.0)
                .travelTimeFromPreviousMinutes(0.0)
                .build();
    }
}
