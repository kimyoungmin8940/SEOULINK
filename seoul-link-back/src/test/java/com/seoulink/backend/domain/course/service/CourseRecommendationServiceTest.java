package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.DailyPlanRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptionResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.model.TransportMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(10, 0))
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
        assertEquals(TransportMode.WALKING, response.getTransportMode());
        assertTrue(response.getEstimatedTravelTimes());
        assertEquals(LocalTime.of(10, 0), response.getDailyStartTime());
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
            assertTrue(option.getTitle().endsWith(option.getOptionName()));
            assertTrue(!option.getDescription().isBlank());
            assertTrue(!option.getRecommendationKey().isBlank());
            assertTrue(option.getRecommendationKey().startsWith("WALKING:"));
            assertTrue(option.getEstimatedTravelTimes());

            for (CourseDayResponse day : option.getDays()) {
                assertEquals(4, day.getPlaces().size());
                assertEquals(2, countCategory(day, "TOUR"));
                assertEquals(1, countCategory(day, "RESTAURANT"));
                assertEquals(1, countCategory(day, "CAFE"));
                assertEquals("10:00", day.getPlaces().get(0).getVisitTime());
                day.getPlaces().forEach(place -> {
                    assertEquals(
                            "서울 " + place.getPlaceName(),
                            place.getAddress()
                    );
                    assertEquals(
                            "https://example.com/places/" + place.getPlaceId() + ".jpg",
                            place.getImageUrl()
                    );
                });
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
        for (int dayIndex = 0; dayIndex < 2; dayIndex++) {
            Set<Long> firstPlaceIds = new HashSet<>();
            for (CourseOptionResponse option : response.getCourseOptions()) {
                firstPlaceIds.add(
                        option.getDays().get(dayIndex).getPlaces().get(0).getPlaceId()
                );
            }
            assertEquals(3, firstPlaceIds.size());
        }
        assertTrue(response.getCourseOptions().stream()
                .allMatch(option -> option.getTotalCourseTimeMinutes() > 0));
    }

    @Test
    @DisplayName("후보 카테고리 7·4·4를 최종 6곳 비율로 축소해 추천한다")
    void scaleCandidateCategoryTargetsToFinalPlaceCount() {
        List<PlaceCandidateDto> candidates = new ArrayList<>();
        for (long id = 1; id <= 7; id++) {
            candidates.add(candidate(
                    id,
                    "관광지 " + id,
                    "TOUR",
                    100.0 - id,
                    37.56 + id * 0.0001,
                    126.97 + id * 0.0001
            ));
        }
        for (long id = 11; id <= 14; id++) {
            candidates.add(candidate(
                    id,
                    "식당 " + id,
                    "RESTAURANT",
                    100.0 - id,
                    37.56 + id * 0.0001,
                    126.97 + id * 0.0001
            ));
        }
        for (long id = 21; id <= 24; id++) {
            candidates.add(candidate(
                    id,
                    "카페 " + id,
                    "CAFE",
                    100.0 - id,
                    37.56 + id * 0.0001,
                    126.97 + id * 0.0001
            ));
        }

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .transportMode(TransportMode.DRIVING)
                .dailyStartTime(LocalTime.of(10, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 20))
                        .targetPlaceCount(6)
                        .categoryTargets(Map.of(
                                "TOUR", 7,
                                "RESTAURANT", 4,
                                "CAFE", 4,
                                "HOTEL", 0
                        ))
                        .placeCandidates(candidates)
                        .build()))
                .build();

        CourseRecommendResponse response =
                courseRecommendationService.recommend(request);

        assertEquals(TransportMode.DRIVING, response.getTransportMode());
        for (CourseOptionResponse option : response.getCourseOptions()) {
            CourseDayResponse day = option.getDays().get(0);
            assertEquals(6, day.getPlaces().size());
            assertEquals(3, countCategory(day, "TOUR"));
            assertEquals(2, countCategory(day, "RESTAURANT"));
            assertEquals(1, countCategory(day, "CAFE"));
        }
    }

    @Test
    @DisplayName("같은 취향으로 다시 추천하면 직전 세 코스를 제외한 새 코스를 반환한다")
    void recommendAgainExcludesPreviousOptions() {
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(10, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 20))
                        .targetPlaceCount(2)
                        .categoryTargets(Map.of(
                                "TOUR", 1,
                                "RESTAURANT", 1,
                                "CAFE", 0,
                                "HOTEL", 0
                        ))
                        .placeCandidates(List.of(
                                candidate(1L, "관광지 1", "TOUR", 96.0, 37.50, 127.00),
                                candidate(2L, "관광지 2", "TOUR", 92.0, 37.51, 127.01),
                                candidate(3L, "관광지 3", "TOUR", 88.0, 37.52, 127.02),
                                candidate(4L, "관광지 4", "TOUR", 84.0, 37.53, 127.03),
                                candidate(11L, "식당 1", "RESTAURANT", 95.0, 37.50, 127.01),
                                candidate(12L, "식당 2", "RESTAURANT", 90.0, 37.51, 127.02),
                                candidate(13L, "식당 3", "RESTAURANT", 85.0, 37.52, 127.03)
                        ))
                        .build()))
                .build();

        CourseRecommendResponse firstResponse =
                courseRecommendationService.recommend(request);
        Set<String> firstKeys = firstResponse.getCourseOptions().stream()
                .map(CourseOptionResponse::getRecommendationKey)
                .collect(java.util.stream.Collectors.toSet());

        request.setExcludedRecommendationKeys(List.copyOf(firstKeys));
        CourseRecommendResponse secondResponse =
                courseRecommendationService.recommend(request);
        Set<String> secondKeys = secondResponse.getCourseOptions().stream()
                .map(CourseOptionResponse::getRecommendationKey)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(3, firstKeys.size());
        assertEquals(3, secondKeys.size());
        assertTrue(firstKeys.stream().noneMatch(secondKeys::contains));
    }

    @Test
    @DisplayName("재추천은 이전 장소를 감점하고 새 장소를 우선한다")
    void recommendAgainPenalizesPreviousPlaces() {
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(10, 0))
                .previouslyRecommendedPlaceIds(List.of(1L, 11L))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 20))
                        .targetPlaceCount(2)
                        .categoryTargets(Map.of(
                                "TOUR", 1,
                                "RESTAURANT", 1,
                                "CAFE", 0,
                                "HOTEL", 0
                        ))
                        .placeCandidates(List.of(
                                candidate(1L, "이전 관광지", "TOUR", 95.0, 37.50, 127.00),
                                candidate(2L, "새 관광지 1", "TOUR", 90.0, 37.51, 127.01),
                                candidate(3L, "새 관광지 2", "TOUR", 85.0, 37.52, 127.02),
                                candidate(11L, "이전 식당", "RESTAURANT", 94.0, 37.50, 127.01),
                                candidate(12L, "새 식당 1", "RESTAURANT", 89.0, 37.51, 127.02),
                                candidate(13L, "새 식당 2", "RESTAURANT", 84.0, 37.52, 127.03)
                        ))
                        .build()))
                .build();

        CourseRecommendResponse response =
                courseRecommendationService.recommend(request);
        Set<Long> preferencePlaceIds = response.getCourseOptions().get(0)
                .getDays().get(0).getPlaces().stream()
                .map(place -> place.getPlaceId())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(!preferencePlaceIds.contains(1L));
        assertTrue(!preferencePlaceIds.contains(11L));
        assertTrue(response.getCourseOptions().stream()
                .flatMap(option -> option.getDays().stream())
                .flatMap(day -> day.getPlaces().stream())
                .allMatch(place -> place.getRecommendationScore() >= 70.0
                        && place.getRecommendationScore() <= 95.0));
    }

    @Test
    @DisplayName("2일 이상 일정은 마지막 날을 제외한 DAY 끝에 같은 숙소를 붙인다")
    void appendSameHotelBeforeFinalDay() {
        LocalDate firstDate = LocalDate.of(2026, 7, 20);
        LocalDate secondDate = LocalDate.of(2026, 7, 21);
        LocalDate finalDate = LocalDate.of(2026, 7, 22);
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(10, 0))
                .hotelCandidates(List.of(
                        candidate(100L, "공통 숙소", "HOTEL", 95.0, 37.55, 127.02),
                        candidate(101L, "예비 숙소 1", "HOTEL", 90.0, 37.56, 127.03),
                        candidate(102L, "예비 숙소 2", "HOTEL", 85.0, 37.57, 127.04)
                ))
                .dailyPlans(List.of(
                        DailyPlanRequest.builder()
                                .visitDate(firstDate)
                                .targetPlaceCount(2)
                                .categoryTargets(Map.of(
                                        "TOUR", 1,
                                        "RESTAURANT", 1,
                                        "CAFE", 0,
                                        "HOTEL", 0
                                ))
                                .placeCandidates(List.of(
                                        candidate(1L, "첫날 관광지 1", "TOUR", 95.0, 37.50, 127.00),
                                        candidate(2L, "첫날 관광지 2", "TOUR", 90.0, 37.51, 127.01),
                                        candidate(11L, "첫날 식당 1", "RESTAURANT", 94.0, 37.50, 127.01),
                                        candidate(12L, "첫날 식당 2", "RESTAURANT", 89.0, 37.51, 127.02)
                                ))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(secondDate)
                                .targetPlaceCount(2)
                                .categoryTargets(Map.of(
                                        "TOUR", 1,
                                        "RESTAURANT", 1,
                                        "CAFE", 0,
                                        "HOTEL", 0
                                ))
                                .placeCandidates(List.of(
                                        candidate(21L, "둘째날 관광지 1", "TOUR", 95.0, 37.53, 127.04),
                                        candidate(22L, "둘째날 관광지 2", "TOUR", 90.0, 37.54, 127.05),
                                        candidate(31L, "둘째날 식당 1", "RESTAURANT", 94.0, 37.53, 127.05),
                                        candidate(32L, "둘째날 식당 2", "RESTAURANT", 89.0, 37.54, 127.06)
                                ))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(finalDate)
                                .targetPlaceCount(2)
                                .categoryTargets(Map.of(
                                        "TOUR", 1,
                                        "RESTAURANT", 1,
                                        "CAFE", 0,
                                        "HOTEL", 0
                                ))
                                .placeCandidates(List.of(
                                        candidate(41L, "마지막날 관광지 1", "TOUR", 95.0, 37.56, 127.07),
                                        candidate(42L, "마지막날 관광지 2", "TOUR", 90.0, 37.57, 127.08),
                                        candidate(51L, "마지막날 식당 1", "RESTAURANT", 94.0, 37.56, 127.08),
                                        candidate(52L, "마지막날 식당 2", "RESTAURANT", 89.0, 37.57, 127.09)
                                ))
                                .build()
                ))
                .build();

        CourseRecommendResponse response =
                courseRecommendationService.recommend(request);

        Set<Long> selectedHotelIds = new HashSet<>();
        for (CourseOptionResponse option : response.getCourseOptions()) {
            assertEquals(8, option.getPlaceCount());
            assertEquals(3, option.getDays().size());

            Long optionHotelId = null;
            for (int dayIndex = 0; dayIndex < 2; dayIndex++) {
                CourseDayResponse day = option.getDays().get(dayIndex);
                assertEquals(3, day.getPlaces().size());
                Long dailyHotelId = day.getPlaces()
                        .get(day.getPlaces().size() - 1)
                        .getPlaceId();
                if (optionHotelId == null) {
                    optionHotelId = dailyHotelId;
                }
                assertEquals(optionHotelId, dailyHotelId);
                assertEquals(
                        "HOTEL",
                        day.getPlaces().get(day.getPlaces().size() - 1).getCategory()
                );
                assertTrue(Boolean.TRUE.equals(
                        day.getPlaces().get(day.getPlaces().size() - 1)
                                .getRouteEstimated()
                ));
            }

            CourseDayResponse finalDay = option.getDays().get(2);
            assertEquals(finalDate, finalDay.getVisitDate());
            assertEquals(2, finalDay.getPlaces().size());
            assertTrue(finalDay.getPlaces().stream()
                    .noneMatch(place -> "HOTEL".equals(place.getCategory())));
            selectedHotelIds.add(optionHotelId);
        }
        assertEquals(3, selectedHotelIds.size());
    }

    @Test
    @DisplayName("P형 48개 후보는 최초·재추천 6개 코스에서 이전 장소를 재사용하지 않는다")
    void packedCandidatePoolSupportsSixDistinctOptions() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        List<PlaceCandidateDto> candidates = packedCandidates();
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(9, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(visitDate)
                        .targetPlaceCount(6)
                        .categoryTargets(Map.of(
                                "TOUR", 24,
                                "RESTAURANT", 16,
                                "CAFE", 8,
                                "HOTEL", 0
                        ))
                        .placeCandidates(candidates)
                        .build()))
                .build();

        CourseRecommendResponse first =
                courseRecommendationService.recommend(request);
        Set<Long> firstPlaceIds = ordinaryPlaceIds(first);
        assertEquals(18, firstPlaceIds.size());
        assertDailyOverlapAtMost(first, 2);

        request.setExcludedRecommendationKeys(first.getCourseOptions().stream()
                .map(CourseOptionResponse::getRecommendationKey)
                .toList());
        request.setPreviouslyRecommendedPlaceIds(
                List.copyOf(firstPlaceIds)
        );
        CourseRecommendResponse second =
                courseRecommendationService.recommend(request);
        Set<Long> secondPlaceIds = ordinaryPlaceIds(second);

        assertEquals(18, secondPlaceIds.size());
        assertTrue(firstPlaceIds.stream()
                .noneMatch(secondPlaceIds::contains));
        assertDailyOverlapAtMost(second, 2);
    }

    @Test
    @DisplayName("categoryTargets 합계가 targetPlaceCount보다 작으면 요청을 거부한다")
    void rejectMismatchedTargetCount() {
        Map<String, Integer> invalidTargets = new LinkedHashMap<>();
        invalidTargets.put("TOUR", 1);
        invalidTargets.put("RESTAURANT", 1);
        invalidTargets.put("CAFE", 1);
        invalidTargets.put("HOTEL", 0);

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .transportMode(TransportMode.WALKING)
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
                .transportMode(TransportMode.WALKING)
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

    @Test
    @DisplayName("이동수단이 없으면 추천 요청을 거부한다")
    void rejectMissingTransportMode() {
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .dailyStartTime(LocalTime.of(10, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 20))
                        .targetPlaceCount(1)
                        .categoryTargets(Map.of(
                                "TOUR", 1,
                                "RESTAURANT", 0,
                                "CAFE", 0,
                                "HOTEL", 0
                        ))
                        .placeCandidates(List.of(candidate(
                                1L,
                                "관광지",
                                "TOUR",
                                90.0,
                                37.5,
                                127.0
                        )))
                        .build()))
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseRecommendationService.recommend(request)
        );
        assertTrue(exception.getMessage().contains("이동수단"));
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

    private List<PlaceCandidateDto> packedCandidates() {
        List<PlaceCandidateDto> candidates = new ArrayList<>();
        for (long id = 1; id <= 24; id++) {
            candidates.add(candidate(
                    id,
                    "관광지 " + id,
                    "TOUR",
                    96.0 - id * 0.3,
                    37.50 + id * 0.001,
                    126.90 + id * 0.001
            ));
        }
        for (long id = 101; id <= 116; id++) {
            candidates.add(candidate(
                    id,
                    "식당 " + id,
                    "RESTAURANT",
                    95.0 - (id - 100) * 0.3,
                    37.52 + (id - 100) * 0.001,
                    126.92 + (id - 100) * 0.001
            ));
        }
        for (long id = 201; id <= 208; id++) {
            candidates.add(candidate(
                    id,
                    "카페 " + id,
                    "CAFE",
                    94.0 - (id - 200) * 0.3,
                    37.54 + (id - 200) * 0.001,
                    126.94 + (id - 200) * 0.001
            ));
        }
        return candidates;
    }

    private Set<Long> ordinaryPlaceIds(CourseRecommendResponse response) {
        return response.getCourseOptions().stream()
                .flatMap(option -> option.getDays().stream())
                .flatMap(day -> day.getPlaces().stream())
                .filter(place -> !"HOTEL".equals(place.getCategory()))
                .map(place -> place.getPlaceId())
                .collect(java.util.stream.Collectors.toSet());
    }

    private void assertDailyOverlapAtMost(
            CourseRecommendResponse response,
            int limit
    ) {
        List<Set<Long>> optionPlaceIds = response.getCourseOptions().stream()
                .map(option -> option.getDays().get(0).getPlaces().stream()
                        .filter(place -> !"HOTEL".equals(place.getCategory()))
                        .map(place -> place.getPlaceId())
                        .collect(java.util.stream.Collectors.toSet()))
                .toList();

        for (int left = 0; left < optionPlaceIds.size(); left++) {
            for (int right = left + 1;
                    right < optionPlaceIds.size();
                    right++) {
                Set<Long> overlap = new HashSet<>(optionPlaceIds.get(left));
                overlap.retainAll(optionPlaceIds.get(right));
                assertTrue(overlap.size() <= limit);
            }
        }
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
                .address("서울 " + placeName)
                .roadAddress("서울로 " + placeId)
                .imageUrl("https://example.com/places/" + placeId + ".jpg")
                .recommendationScore(score)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

}
