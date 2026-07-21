package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.DailyPlanRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptionResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 최종 후보 풀에서 카테고리 목표를 지키는 세 가지 코스를 만드는 흐름을 검증한다. */
class CourseRecommendationServiceTest {

    private CourseRecommendationService courseRecommendationService;

    @BeforeEach
    void setUp() {
        DistanceService distanceService = new DistanceService(null);
        CourseOptimizationService optimizationService =
                new CourseOptimizationService(
                        distanceService,
                        new VisitDurationService()
                );
        courseRecommendationService = new CourseRecommendationService(
                optimizationService,
                distanceService
        );
    }

    @Test
    @DisplayName("날짜별 목표 수량과 카테고리를 지키는 서로 다른 코스 3개를 반환한다")
    void recommendThreeCourseOptions() {
        LocalDate firstDate = LocalDate.of(2026, 7, 20);
        LocalDate secondDate = LocalDate.of(2026, 7, 21);
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .travelCode("ATLSR")
                .dailyStartTime(LocalTime.of(10, 0))
                .weatherStatus("rain")
                .temperature(27.5)
                .rainProbability(80)
                .dailyPlans(List.of(
                        DailyPlanRequest.builder()
                                .visitDate(firstDate)
                                .targetPlaceCount(4)
                                .categoryTargets(categoryTargets())
                                .placeCandidates(List.of(
                                        candidate(10L, "경복궁", "TOUR", 92.0, 37.5796, 126.9770),
                                        candidate(11L, "덕수궁", "TOUR", 89.0, 37.5658, 126.9751),
                                        candidate(12L, "청계천", "TOUR", 86.0, 37.5690, 126.9784),
                                        candidate(40L, "광화문 식당", "RESTAURANT", 89.0, 37.5701, 126.9768),
                                        candidate(42L, "종로 맛집", "RESTAURANT", 84.0, 37.5704, 126.9921),
                                        candidate(50L, "서촌 카페", "CAFE", 87.0, 37.5792, 126.9693)
                                ))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(secondDate)
                                .targetPlaceCount(4)
                                .categoryTargets(categoryTargets())
                                .placeCandidates(List.of(
                                        candidate(30L, "서울숲", "TOUR", 94.0, 37.5444, 127.0374),
                                        candidate(32L, "성수 연무장길", "TOUR", 88.0, 37.5435, 127.0557),
                                        candidate(33L, "건대 커먼그라운드", "TOUR", 82.0, 37.5410, 127.0669),
                                        candidate(43L, "성수 식당", "RESTAURANT", 91.0, 37.5448, 127.0552),
                                        candidate(52L, "성수 카페", "CAFE", 90.0, 37.5443, 127.0580),
                                        candidate(54L, "뚝섬 카페", "CAFE", 82.0, 37.5310, 127.0670)
                                ))
                                .build()
                ))
                .build();

        CourseRecommendResponse response =
                courseRecommendationService.recommend(request);

        assertEquals(101L, response.getResultId());
        assertEquals("ATLSR", response.getTravelCode());
        assertEquals(LocalTime.of(10, 0), response.getDailyStartTime());
        assertEquals("RAIN", response.getWeatherStatus());
        assertEquals(27.5, response.getTemperature());
        assertEquals(80, response.getRainProbability());
        assertEquals(3, response.getOptionCount());
        assertEquals(3, response.getCourseOptions().size());
        assertEquals("PREFERENCE", response.getCourseOptions().get(0).getOptionType());
        assertEquals("MIN_DISTANCE", response.getCourseOptions().get(1).getOptionType());
        assertEquals("BALANCED", response.getCourseOptions().get(2).getOptionType());

        Set<String> optionSignatures = new HashSet<>();
        for (CourseOptionResponse option : response.getCourseOptions()) {
            assertEquals(8, option.getPlaceCount());
            assertEquals(2, option.getDayCount());
            assertEquals(2, option.getDays().size());

            for (CourseDayResponse day : option.getDays()) {
                assertEquals(4, day.getPlaces().size());
                assertEquals(2, countCategory(day, "TOUR"));
                assertEquals(1, countCategory(day, "RESTAURANT"));
                assertEquals(1, countCategory(day, "CAFE"));
                assertEquals("10:00", day.getPlaces().get(0).getVisitTime());
            }

            String signature = option.getDays().stream()
                    .flatMap(day -> day.getPlaces().stream())
                    .map(place -> place.getPlaceId().toString())
                    .sorted()
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            optionSignatures.add(signature);
        }
        assertEquals(3, optionSignatures.size());
        assertTrue(response.getCourseOptions().stream()
                .allMatch(option -> option.getTotalCourseTimeMinutes() > 0));
    }

    @Test
    @DisplayName("강수 확률이 0~100 범위를 벗어나면 요청을 거부한다")
    void rejectInvalidRainProbability() {
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .dailyStartTime(LocalTime.of(10, 0))
                .weatherStatus("RAIN")
                .temperature(27.5)
                .rainProbability(101)
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 20))
                        .targetPlaceCount(1)
                        .categoryTargets(Map.of(
                                "TOUR", 1,
                                "RESTAURANT", 0,
                                "CAFE", 0,
                                "HOTEL", 0
                        ))
                        .placeCandidates(List.of(
                                candidate(1L, "관광지", "TOUR", 90.0, 37.5, 127.0)
                        ))
                        .build()))
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseRecommendationService.recommend(request)
        );
        assertEquals(
                "rainProbability는 0 이상 100 이하이어야 합니다.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("categoryTargets 합계가 targetPlaceCount와 다르면 요청을 거부한다")
    void rejectMismatchedTargetCount() {
        Map<String, Integer> invalidTargets = new LinkedHashMap<>();
        invalidTargets.put("TOUR", 1);
        invalidTargets.put("RESTAURANT", 1);
        invalidTargets.put("CAFE", 1);
        invalidTargets.put("HOTEL", 0);

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .dailyStartTime(LocalTime.of(10, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 20))
                        .targetPlaceCount(4)
                        .categoryTargets(invalidTargets)
                        .placeCandidates(List.of(
                                candidate(1L, "관광지", "TOUR", 90.0, 37.5, 127.0),
                                candidate(2L, "식당", "RESTAURANT", 90.0, 37.5, 127.0),
                                candidate(3L, "카페", "CAFE", 90.0, 37.5, 127.0)
                        ))
                        .build()))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> courseRecommendationService.recommend(request)
        );
    }

    @Test
    @DisplayName("카테고리 후보가 목표 개수보다 적으면 요청을 거부한다")
    void rejectInsufficientCategoryCandidates() {
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .dailyStartTime(LocalTime.of(10, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 20))
                        .targetPlaceCount(4)
                        .categoryTargets(categoryTargets())
                        .placeCandidates(List.of(
                                candidate(1L, "관광지", "TOUR", 90.0, 37.5, 127.0),
                                candidate(2L, "식당", "RESTAURANT", 90.0, 37.5, 127.0),
                                candidate(3L, "카페", "CAFE", 90.0, 37.5, 127.0),
                                candidate(4L, "다른 카페", "CAFE", 80.0, 37.5, 127.0)
                        ))
                        .build()))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> courseRecommendationService.recommend(request)
        );
    }

    private Map<String, Integer> categoryTargets() {
        Map<String, Integer> targets = new LinkedHashMap<>();
        targets.put("TOUR", 2);
        targets.put("RESTAURANT", 1);
        targets.put("CAFE", 1);
        targets.put("HOTEL", 0);
        return targets;
    }

    private long countCategory(CourseDayResponse day, String category) {
        return day.getPlaces().stream()
                .filter(place -> category.equals(place.getCategory()))
                .count();
    }

    private PlaceCandidateDto candidate(
            Long placeId,
            String placeName,
            String category,
            Double score,
            Double latitude,
            Double longitude
    ) {
        return PlaceCandidateDto.builder()
                .placeId(placeId)
                .placeName(placeName)
                .category(category)
                .recommendationScore(score)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }
}
