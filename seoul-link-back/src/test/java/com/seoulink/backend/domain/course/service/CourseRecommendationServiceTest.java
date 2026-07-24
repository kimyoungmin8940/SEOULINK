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
                .transportMode(TransportMode.DRIVING)
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
        assertEquals(TransportMode.DRIVING, response.getTransportMode());
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
            assertTrue(option.getRecommendationKey().startsWith("DRIVING:"));
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
    @DisplayName("균형 코스는 정규화된 이동비용으로 먼 고득점 조합보다 가까운 조합을 선택한다")
    void balancedCourseUsesNormalizedTravelAndDistanceScores() {
        DistanceService distanceService = new DistanceService() {
            private RouteMatrix createMatrix(List<PlaceCandidateDto> places) {
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
                        double travelMinutes = 5.0;
                        double distanceKm = 0.5;
                        if (smaller == 1L && larger == 2L) {
                            travelMinutes = 100.0;
                            distanceKm = 10.0;
                        } else if ((smaller == 1L || smaller == 2L)
                                && larger == 4L) {
                            travelMinutes = 6.0;
                            distanceKm = 0.6;
                        }
                        distances[from][to] = distanceKm;
                        travelTimes[from][to] = travelMinutes;
                    }
                }
                return new RouteMatrix(
                        distances,
                        travelTimes,
                        true
                );
            }

            @Override
            public RouteMatrix calculateCandidatePoolMatrix(
                    List<PlaceCandidateDto> places,
                    TransportMode transportMode
            ) {
                return createMatrix(places);
            }

            @Override
            public RouteMatrix calculateRouteMatrix(
                    List<PlaceCandidateDto> places,
                    TransportMode transportMode
            ) {
                return createMatrix(places);
            }
        };
        CourseRecommendationService service = new CourseRecommendationService(
                new CourseOptimizationService(
                        distanceService,
                        new VisitDurationService()
                ),
                distanceService
        );
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(202L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.DRIVING)
                .dailyStartTime(LocalTime.of(10, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 20))
                        .targetPlaceCount(2)
                        .categoryTargets(Map.of("TOUR", 2))
                        .placeCandidates(List.of(
                                candidate(1L, "최고점 장소", "TOUR",
                                        100.0, 37.501, 127.001),
                                candidate(2L, "고득점이지만 먼 장소", "TOUR",
                                        95.0, 37.502, 127.002),
                                candidate(3L, "가까운 장소", "TOUR",
                                        70.0, 37.503, 127.003),
                                candidate(4L, "다른 가까운 장소", "TOUR",
                                        70.0, 37.504, 127.004)
                        ))
                        .build()))
                .build();

        CourseOptionResponse balanced = service.recommend(request)
                .getCourseOptions()
                .get(2);
        List<Long> balancedPlaceIds = balanced.getDays().get(0)
                .getPlaces().stream()
                .map(place -> place.getPlaceId())
                .toList();

        assertTrue(balancedPlaceIds.contains(1L));
        assertTrue(balancedPlaceIds.contains(3L)
                || balancedPlaceIds.contains(4L));
        assertTrue(!balancedPlaceIds.contains(2L));
        assertEquals(5.0, balanced.getTotalTravelTimeMinutes(), 0.000001);
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
                .dailyStartTime(LocalTime.of(11, 0))
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
            assertEquals("TOUR", day.getPlaces().get(0).getCategory());

            LocalTime firstRestaurantTime = day.getPlaces().stream()
                    .filter(place -> "RESTAURANT".equals(place.getCategory()))
                    .map(place -> LocalTime.parse(place.getVisitTime()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(!firstRestaurantTime.isBefore(LocalTime.of(11, 30)));
            assertTrue(!firstRestaurantTime.isAfter(LocalTime.of(14, 0)));

            for (int index = 1; index < day.getPlaces().size(); index++) {
                String previousCategory = day.getPlaces().get(index - 1)
                        .getCategory();
                String currentCategory = day.getPlaces().get(index)
                        .getCategory();
                if (previousCategory.equals(currentCategory)) {
                    assertTrue(!"RESTAURANT".equals(currentCategory)
                            && !"CAFE".equals(currentCategory));
                }
            }
        }
    }

    @Test
    @DisplayName("같은 취향으로 다시 추천하면 직전 세 코스를 제외한 새 코스를 반환한다")
    void recommendAgainExcludesPreviousOptions() {
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.DRIVING)
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
                .transportMode(TransportMode.DRIVING)
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
                .transportMode(TransportMode.DRIVING)
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
                .transportMode(TransportMode.DRIVING)
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
        assertDailyOverlapAtMost(first, 0);

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
        assertDailyOverlapAtMost(second, 0);
    }

    @Test
    @DisplayName("후보가 부족하면 필요한 중복만 허용하고 한 코스에 겹침이 몰리지 않는다")
    void limitedCandidatePoolUsesOnlyNecessaryOverlap() {
        LocalDate visitDate = LocalDate.of(2026, 7, 24);
        List<PlaceCandidateDto> candidates = new ArrayList<>();
        for (long id = 1; id <= 4; id++) {
            candidates.add(candidate(
                    id,
                    "관광지 " + id,
                    "TOUR",
                    100.0 - id,
                    37.50 + id * 0.001,
                    127.00 + id * 0.001
            ));
        }
        for (long id = 11; id <= 13; id++) {
            candidates.add(candidate(
                    id,
                    "식당 " + id,
                    "RESTAURANT",
                    100.0 - id,
                    37.51 + id * 0.001,
                    127.01 + id * 0.001
            ));
        }
        for (long id = 21; id <= 23; id++) {
            candidates.add(candidate(
                    id,
                    "카페 " + id,
                    "CAFE",
                    100.0 - id,
                    37.52 + id * 0.001,
                    127.02 + id * 0.001
            ));
        }

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(101L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.DRIVING)
                .dailyStartTime(LocalTime.of(13, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(visitDate)
                        .targetPlaceCount(4)
                        .categoryTargets(Map.of(
                                "TOUR", 4,
                                "RESTAURANT", 3,
                                "CAFE", 3,
                                "HOTEL", 0
                        ))
                        .placeCandidates(candidates)
                        .build()))
                .build();

        CourseRecommendResponse response =
                courseRecommendationService.recommend(request);

        // 3개 코스 12자리 중 후보는 10개이므로 관광지 2자리만 재사용되어야 한다.
        assertEquals(10, ordinaryPlaceIds(response).size());
        assertDailyOverlapAtMost(response, 1);
    }

    @Test
    @DisplayName("도보 목표 장소 수가 불가능하면 평균 18분과 구간 20분을 지키며 장소 수를 줄인다")
    void walkingRecommendationReducesPlacesWithoutBreakingLimits() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(
                        pairKey(1L, 2L), 16.0,
                        pairKey(2L, 3L), 16.0,
                        pairKey(3L, 4L), 16.0
                ),
                120.0
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 24);
        List<PlaceCandidateDto> candidates = List.of(
                candidate(1L, "도보 장소 1", "TOUR", 96.0, 37.50, 127.00),
                candidate(2L, "도보 장소 2", "TOUR", 95.0, 37.51, 127.01),
                candidate(3L, "도보 장소 3", "TOUR", 94.0, 37.52, 127.02),
                candidate(4L, "도보 장소 4", "TOUR", 93.0, 37.53, 127.03),
                candidate(5L, "고립 장소 5", "TOUR", 92.0, 37.54, 127.04),
                candidate(6L, "고립 장소 6", "TOUR", 91.0, 37.55, 127.05)
        );

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(112L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(visitDate)
                        .targetPlaceCount(6)
                        .categoryTargets(Map.of(
                                "TOUR", 6,
                                "RESTAURANT", 0,
                                "CAFE", 0,
                                "HOTEL", 0
                        ))
                        .placeCandidates(candidates)
                        .build()))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        assertEquals(3, response.getOptionCount());
        for (CourseOptionResponse option : response.getCourseOptions()) {
            CourseDayResponse day = option.getDays().get(0);
            assertEquals(4, day.getPlaces().size());
            assertTrue(!option.getEstimatedTravelTimes());
            assertWalkingLimits(day);
            assertEquals(48.0, option.getTotalTravelTimeMinutes(), 0.000001);
        }
    }

    @Test
    @DisplayName("도보는 고득점 평균 17분 경로보다 평균 15분 이하 경로를 먼저 선택한다")
    void walkingRecommendationPrefersFifteenMinuteTier() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(
                        pairKey(1L, 2L), 17.0,
                        pairKey(2L, 3L), 17.0,
                        pairKey(4L, 5L), 10.0,
                        pairKey(5L, 6L), 10.0
                ),
                120.0
        );
        List<PlaceCandidateDto> candidates = List.of(
                candidate(1L, "고득점 장소 1", "TOUR", 100.0, 37.50, 127.00),
                candidate(2L, "고득점 장소 2", "TOUR", 99.0, 37.51, 127.01),
                candidate(3L, "고득점 장소 3", "TOUR", 98.0, 37.52, 127.02),
                candidate(4L, "가까운 장소 4", "TOUR", 80.0, 37.53, 127.03),
                candidate(5L, "가까운 장소 5", "TOUR", 79.0, 37.54, 127.04),
                candidate(6L, "가까운 장소 6", "TOUR", 78.0, 37.55, 127.05)
        );

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(113L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 24))
                        .targetPlaceCount(3)
                        .categoryTargets(Map.of(
                                "TOUR", 3,
                                "RESTAURANT", 0,
                                "CAFE", 0,
                                "HOTEL", 0
                        ))
                        .placeCandidates(candidates)
                        .build()))
                .build();

        CourseOptionResponse preference = service.recommend(request)
                .getCourseOptions()
                .get(0);
        Set<Long> selectedIds = preference.getDays().get(0).getPlaces()
                .stream()
                .map(place -> place.getPlaceId())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of(4L, 5L, 6L), selectedIds);
        assertEquals(20.0, preference.getTotalTravelTimeMinutes(), 0.000001);
        assertWalkingLimits(preference.getDays().get(0));
    }

    @Test
    @DisplayName("도보 평균 15분 경로가 없으면 장소 수를 유지한 채 평균 18분 단계로 완화한다")
    void walkingRecommendationUsesEighteenMinuteTierBeforeReducingPlaces() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(
                        pairKey(1L, 2L), 17.0,
                        pairKey(2L, 3L), 17.0
                ),
                120.0
        );
        List<PlaceCandidateDto> candidates = List.of(
                candidate(1L, "도보 장소 1", "TOUR", 96.0, 37.50, 127.00),
                candidate(2L, "도보 장소 2", "TOUR", 95.0, 37.51, 127.01),
                candidate(3L, "도보 장소 3", "TOUR", 94.0, 37.52, 127.02)
        );

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(114L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 24))
                        .targetPlaceCount(3)
                        .categoryTargets(Map.of(
                                "TOUR", 3,
                                "RESTAURANT", 0,
                                "CAFE", 0,
                                "HOTEL", 0
                        ))
                        .placeCandidates(candidates)
                        .build()))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        for (CourseOptionResponse option : response.getCourseOptions()) {
            CourseDayResponse day = option.getDays().get(0);
            assertEquals(3, day.getPlaces().size());
            assertEquals(34.0, option.getTotalTravelTimeMinutes(), 0.000001);
            assertWalkingLimits(day);
        }
    }

    @Test
    @DisplayName("도보 구간당 20분 제한은 장소 수를 줄여도 절대 완화하지 않는다")
    void walkingRecommendationNeverRelaxesTwentyMinuteLegLimit() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(pairKey(1L, 2L), 21.0),
                120.0
        );
        List<PlaceCandidateDto> candidates = List.of(
                candidate(1L, "도보 장소 1", "TOUR", 96.0, 37.50, 127.00),
                candidate(2L, "도보 장소 2", "TOUR", 95.0, 37.51, 127.01)
        );

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(115L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 24))
                        .targetPlaceCount(2)
                        .categoryTargets(Map.of(
                                "TOUR", 2,
                                "RESTAURANT", 0,
                                "CAFE", 0,
                                "HOTEL", 0
                        ))
                        .placeCandidates(candidates)
                        .build()))
                .build();

        assertThrows(
                IllegalStateException.class,
                () -> service.recommend(request)
        );
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

    private CourseRecommendationService actualWalkingService(
            Map<String, Double> pairMinutes,
            double defaultMinutes
    ) {
        DistanceService distanceService = new DistanceService() {
            private RouteMatrix createMatrix(
                    List<PlaceCandidateDto> places
            ) {
                int size = places.size();
                double[][] distances = new double[size][size];
                double[][] travelTimes = new double[size][size];
                for (int from = 0; from < size; from++) {
                    for (int to = 0; to < size; to++) {
                        if (from == to) {
                            continue;
                        }
                        double minutes = pairMinutes.getOrDefault(
                                pairKey(
                                        places.get(from).getPlaceId(),
                                        places.get(to).getPlaceId()
                                ),
                                defaultMinutes
                        );
                        travelTimes[from][to] = minutes;
                        distances[from][to] = minutes / 12.0;
                    }
                }
                return new RouteMatrix(
                        distances,
                        travelTimes,
                        false
                );
            }

            @Override
            public RouteMatrix calculateCandidatePoolMatrix(
                    List<PlaceCandidateDto> places,
                    TransportMode transportMode
            ) {
                return createMatrix(places);
            }

            @Override
            public RouteMatrix calculateRouteMatrix(
                    List<PlaceCandidateDto> places,
                    TransportMode transportMode
            ) {
                return createMatrix(places);
            }
        };
        return new CourseRecommendationService(
                new CourseOptimizationService(
                        distanceService,
                        new VisitDurationService()
                ),
                distanceService
        );
    }

    private String pairKey(Long left, Long right) {
        long minimum = Math.min(left, right);
        long maximum = Math.max(left, right);
        return minimum + ":" + maximum;
    }

    private void assertWalkingLimits(CourseDayResponse day) {
        double totalMinutes = 0.0;
        int legCount = 0;
        for (int index = 1; index < day.getPlaces().size(); index++) {
            double minutes = day.getPlaces().get(index)
                    .getTravelTimeFromPreviousMinutes();
            assertTrue(
                    minutes <= 20.0 + 0.000001,
                    "도보 구간이 20분을 초과했습니다: " + minutes
            );
            assertTrue(
                    !Boolean.TRUE.equals(
                            day.getPlaces().get(index).getRouteEstimated()
                    )
            );
            totalMinutes += minutes;
            legCount++;
        }
        assertTrue(
                totalMinutes <= legCount * 18.0 + 0.000001,
                "도보 평균이 18분을 초과했습니다: total=" + totalMinutes
        );
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
