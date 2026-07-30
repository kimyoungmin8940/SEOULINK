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
                                        candidate(13L, "창덕궁", "TOUR", 84.0, 37.5826, 126.9910),
                                        candidate(14L, "종묘", "TOUR", 82.0, 37.5742, 126.9941),
                                        candidate(15L, "서울역사박물관", "TOUR", 80.0, 37.5705, 126.9705),
                                        candidate(40L, "광화문 식당", "RESTAURANT", 89.0, 37.5701, 126.9768),
                                        candidate(41L, "서촌 식당", "RESTAURANT", 86.0, 37.5785, 126.9720),
                                        candidate(42L, "종로 맛집", "RESTAURANT", 84.0, 37.5704, 126.9921),
                                        candidate(50L, "서촌 카페", "CAFE", 87.0, 37.5792, 126.9693),
                                        candidate(51L, "광화문 카페", "CAFE", 84.0, 37.5710, 126.9760),
                                        candidate(53L, "익선동 카페", "CAFE", 81.0, 37.5740, 126.9890)
                                ))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(secondDate)
                                .targetPlaceCount(4)
                                .categoryTargets(categoryTargets())
                                .placeCandidates(List.of(
                                        candidate(30L, "서울숲", "TOUR", 94.0, 37.5444, 127.0374),
                                        candidate(31L, "뚝섬한강공원", "TOUR", 91.0, 37.5293, 127.0698),
                                        candidate(32L, "성수 연무장길", "TOUR", 88.0, 37.5435, 127.0557),
                                        candidate(33L, "건대 커먼그라운드", "TOUR", 82.0, 37.5410, 127.0669),
                                        candidate(34L, "성수 미술관", "TOUR", 80.0, 37.5420, 127.0540),
                                        candidate(35L, "서울새활용플라자", "TOUR", 78.0, 37.5580, 127.0550),
                                        candidate(43L, "성수 식당", "RESTAURANT", 91.0, 37.5448, 127.0552),
                                        candidate(44L, "서울숲 식당", "RESTAURANT", 87.0, 37.5450, 127.0410),
                                        candidate(45L, "건대 식당", "RESTAURANT", 83.0, 37.5400, 127.0660),
                                        candidate(52L, "성수 카페", "CAFE", 90.0, 37.5443, 127.0580),
                                        candidate(54L, "뚝섬 카페", "CAFE", 82.0, 37.5310, 127.0670),
                                        candidate(55L, "건대 카페", "CAFE", 80.0, 37.5415, 127.0675)
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
                        .targetPlaceCount(3)
                        .categoryTargets(Map.of("TOUR", 3))
                        .placeCandidates(List.of(
                                candidate(1L, "최고점 장소", "TOUR",
                                        100.0, 37.501, 127.001),
                                candidate(2L, "고득점이지만 먼 장소", "TOUR",
                                        95.0, 37.502, 127.002),
                                candidate(3L, "가까운 장소", "TOUR",
                                        70.0, 37.503, 127.003),
                                candidate(4L, "다른 가까운 장소", "TOUR",
                                        70.0, 37.504, 127.004),
                                candidate(5L, "가까운 장소 5", "TOUR",
                                        69.0, 37.505, 127.005),
                                candidate(6L, "가까운 장소 6", "TOUR",
                                        68.0, 37.506, 127.006),
                                candidate(7L, "가까운 장소 7", "TOUR",
                                        67.0, 37.507, 127.007),
                                candidate(8L, "가까운 장소 8", "TOUR",
                                        66.0, 37.508, 127.008),
                                candidate(9L, "가까운 장소 9", "TOUR",
                                        65.0, 37.509, 127.009)
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

        assertTrue(!(balancedPlaceIds.contains(1L)
                && balancedPlaceIds.contains(2L)));
        assertEquals(10.0, balanced.getTotalTravelTimeMinutes(), 0.000001);
    }

    @Test
    @DisplayName("후보 비율과 무관하게 P형 최종 카테고리를 3·2·1로 고정한다")
    void scaleCandidateCategoryTargetsToFinalPlaceCount() {
        List<PlaceCandidateDto> candidates = new ArrayList<>();
        for (long id = 1; id <= 9; id++) {
            candidates.add(candidate(
                    id,
                    "관광지 " + id,
                    "TOUR",
                    100.0 - id,
                    37.56 + id * 0.0001,
                    126.97 + id * 0.0001
            ));
        }
        for (long id = 11; id <= 16; id++) {
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
                .travelCode("ATLSP")
                .transportMode(TransportMode.DRIVING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 20))
                        .targetPlaceCount(6)
                        .categoryTargets(Map.of(
                                "TOUR", 9,
                                "RESTAURANT", 6,
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

            List<LocalTime> restaurantTimes = day.getPlaces().stream()
                    .filter(place -> "RESTAURANT".equals(place.getCategory()))
                    .map(place -> LocalTime.parse(place.getVisitTime()))
                    .toList();
            assertEquals(2, restaurantTimes.size());
            LocalTime firstRestaurantTime = restaurantTimes.get(0);
            assertTrue(!firstRestaurantTime.isBefore(LocalTime.of(11, 30)));
            assertTrue(!firstRestaurantTime.isAfter(LocalTime.of(14, 0)));
            LocalTime secondRestaurantTime = restaurantTimes.get(1);
            assertTrue(!secondRestaurantTime.isBefore(LocalTime.of(17, 30)));
            assertTrue(!secondRestaurantTime.isAfter(LocalTime.of(19, 30)));

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
    @DisplayName("R형은 13시에 식당부터 시작하고 2·1·1 카테고리를 지킨다")
    void relaxedScheduleStartsWithRestaurantAtOnePm() {
        List<PlaceCandidateDto> candidates = new ArrayList<>();
        for (long id = 1; id <= 6; id++) {
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

        CourseRecommendResponse response =
                courseRecommendationService.recommend(
                        CourseRecommendRequest.builder()
                                .resultId(120L)
                                .travelCode("ATLSR")
                                .transportMode(TransportMode.DRIVING)
                                .dailyStartTime(LocalTime.of(13, 0))
                                .dailyPlans(List.of(DailyPlanRequest.builder()
                                        .visitDate(LocalDate.of(2026, 7, 24))
                                        .targetPlaceCount(4)
                                        .categoryTargets(Map.of(
                                                "TOUR", 6,
                                                "RESTAURANT", 3,
                                                "CAFE", 3,
                                                "HOTEL", 0
                                        ))
                                        .placeCandidates(candidates)
                                        .build()))
                                .build()
                );

        for (CourseOptionResponse option : response.getCourseOptions()) {
            CourseDayResponse day = option.getDays().get(0);
            assertEquals(2, countCategory(day, "TOUR"));
            assertEquals(1, countCategory(day, "RESTAURANT"));
            assertEquals(1, countCategory(day, "CAFE"));
            assertEquals("RESTAURANT", day.getPlaces().get(0).getCategory());
            assertEquals("13:00", day.getPlaces().get(0).getVisitTime());
        }
    }

    @Test
    @DisplayName("상위 구 가산점을 코스 선택에 반영하고 실제 중심 구를 반환한다")
    void preferredRegionBonusAndCourseRegionUseSameDistricts() {
        List<PlaceCandidateDto> candidates = new ArrayList<>();
        for (long id = 1; id <= 3; id++) {
            candidates.add(candidateWithRegion(
                    id,
                    "종로 관광지 " + id,
                    "TOUR",
                    93.0,
                    37.570 + id * 0.001,
                    126.980 + id * 0.001,
                    "종로구"
            ));
        }
        for (long id = 11; id <= 16; id++) {
            candidates.add(candidateWithRegion(
                    id,
                    "강남 관광지 " + id,
                    "TOUR",
                    94.0,
                    37.500 + id * 0.00001,
                    127.030 + id * 0.00001,
                    "강남구"
            ));
        }

        CourseRecommendResponse response =
                courseRecommendationService.recommend(
                        CourseRecommendRequest.builder()
                                .resultId(121L)
                                .travelCode("ATLSP")
                                .preferredRegions(List.of("종로구", "강남구"))
                                .transportMode(TransportMode.DRIVING)
                                .dailyStartTime(LocalTime.of(11, 0))
                                .dailyPlans(List.of(DailyPlanRequest.builder()
                                        .visitDate(LocalDate.of(2026, 7, 24))
                                        .targetPlaceCount(3)
                                        .categoryTargets(Map.of("TOUR", 3))
                                        .placeCandidates(candidates)
                                        .build()))
                                .build()
                );

        assertEquals(
                List.of("종로구", "강남구"),
                response.getPreferredRegions()
        );
        CourseOptionResponse preference = response.getCourseOptions().get(0);
        assertEquals("종로구", preference.getRegion());
        assertTrue(preference.getDays().get(0).getPlaces().stream()
                .allMatch(place -> "종로구".equals(place.getRegion())));
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
                        .targetPlaceCount(3)
                        .categoryTargets(Map.of(
                                "TOUR", 2,
                                "RESTAURANT", 1,
                                "CAFE", 0,
                                "HOTEL", 0
                        ))
                        .placeCandidates(List.of(
                                candidate(1L, "관광지 1", "TOUR", 96.0, 37.50, 127.00),
                                candidate(2L, "관광지 2", "TOUR", 92.0, 37.51, 127.01),
                                candidate(3L, "관광지 3", "TOUR", 88.0, 37.52, 127.02),
                                candidate(4L, "관광지 4", "TOUR", 84.0, 37.53, 127.03),
                                candidate(5L, "관광지 5", "TOUR", 80.0, 37.54, 127.04),
                                candidate(11L, "식당 1", "RESTAURANT", 95.0, 37.50, 127.01),
                                candidate(12L, "식당 2", "RESTAURANT", 90.0, 37.51, 127.02),
                                candidate(13L, "식당 3", "RESTAURANT", 85.0, 37.52, 127.03),
                                candidate(14L, "식당 4", "RESTAURANT", 80.0, 37.53, 127.04)
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
                        .targetPlaceCount(3)
                        .categoryTargets(Map.of(
                                "TOUR", 2,
                                "RESTAURANT", 1,
                                "CAFE", 0,
                                "HOTEL", 0
                        ))
                        .placeCandidates(List.of(
                                candidate(1L, "이전 관광지", "TOUR", 95.0, 37.50, 127.00),
                                candidate(2L, "새 관광지 1", "TOUR", 90.0, 37.51, 127.01),
                                candidate(3L, "새 관광지 2", "TOUR", 85.0, 37.52, 127.02),
                                candidate(4L, "새 관광지 3", "TOUR", 80.0, 37.53, 127.03),
                                candidate(5L, "새 관광지 4", "TOUR", 78.0, 37.54, 127.04),
                                candidate(6L, "새 관광지 5", "TOUR", 76.0, 37.55, 127.05),
                                candidate(7L, "새 관광지 6", "TOUR", 74.0, 37.56, 127.06),
                                candidate(11L, "이전 식당", "RESTAURANT", 94.0, 37.50, 127.01),
                                candidate(12L, "새 식당 1", "RESTAURANT", 89.0, 37.51, 127.02),
                                candidate(13L, "새 식당 2", "RESTAURANT", 84.0, 37.52, 127.03),
                                candidate(14L, "새 식당 3", "RESTAURANT", 79.0, 37.53, 127.04),
                                candidate(15L, "새 식당 4", "RESTAURANT", 77.0, 37.54, 127.05)
                        ))
                        .build()))
                .build();

        CourseRecommendResponse response =
                courseRecommendationService.recommend(request);
        Set<Long> preferencePlaceIds = response.getCourseOptions().get(0)
                .getDays().get(0).getPlaces().stream()
                .map(place -> place.getPlaceId())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(
                !preferencePlaceIds.contains(1L),
                "이전 관광지가 다시 선택됨: " + preferencePlaceIds
        );
        assertTrue(
                !preferencePlaceIds.contains(11L),
                "이전 식당이 다시 선택됨: " + preferencePlaceIds
        );
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
                                .targetPlaceCount(3)
                                .categoryTargets(Map.of(
                                        "TOUR", 2,
                                        "RESTAURANT", 1,
                                        "CAFE", 0,
                                        "HOTEL", 0
                                ))
                                .placeCandidates(List.of(
                                        candidate(1L, "첫날 관광지 1", "TOUR", 95.0, 37.50, 127.00),
                                        candidate(2L, "첫날 관광지 2", "TOUR", 90.0, 37.51, 127.01),
                                        candidate(3L, "첫날 관광지 3", "TOUR", 85.0, 37.52, 127.02),
                                        candidate(11L, "첫날 식당 1", "RESTAURANT", 94.0, 37.50, 127.01),
                                        candidate(12L, "첫날 식당 2", "RESTAURANT", 89.0, 37.51, 127.02),
                                        candidate(13L, "첫날 식당 3", "RESTAURANT", 84.0, 37.52, 127.03)
                                ))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(secondDate)
                                .targetPlaceCount(3)
                                .categoryTargets(Map.of(
                                        "TOUR", 2,
                                        "RESTAURANT", 1,
                                        "CAFE", 0,
                                        "HOTEL", 0
                                ))
                                .placeCandidates(List.of(
                                        candidate(21L, "둘째날 관광지 1", "TOUR", 95.0, 37.53, 127.04),
                                        candidate(22L, "둘째날 관광지 2", "TOUR", 90.0, 37.54, 127.05),
                                        candidate(23L, "둘째날 관광지 3", "TOUR", 85.0, 37.55, 127.06),
                                        candidate(31L, "둘째날 식당 1", "RESTAURANT", 94.0, 37.53, 127.05),
                                        candidate(32L, "둘째날 식당 2", "RESTAURANT", 89.0, 37.54, 127.06),
                                        candidate(33L, "둘째날 식당 3", "RESTAURANT", 84.0, 37.55, 127.07)
                                ))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(finalDate)
                                .targetPlaceCount(3)
                                .categoryTargets(Map.of(
                                        "TOUR", 2,
                                        "RESTAURANT", 1,
                                        "CAFE", 0,
                                        "HOTEL", 0
                                ))
                                .placeCandidates(List.of(
                                        candidate(41L, "마지막날 관광지 1", "TOUR", 95.0, 37.56, 127.07),
                                        candidate(42L, "마지막날 관광지 2", "TOUR", 90.0, 37.57, 127.08),
                                        candidate(43L, "마지막날 관광지 3", "TOUR", 85.0, 37.58, 127.09),
                                        candidate(51L, "마지막날 식당 1", "RESTAURANT", 94.0, 37.56, 127.08),
                                        candidate(52L, "마지막날 식당 2", "RESTAURANT", 89.0, 37.57, 127.09),
                                        candidate(53L, "마지막날 식당 3", "RESTAURANT", 84.0, 37.58, 127.10)
                                ))
                                .build()
                ))
                .build();

        CourseRecommendResponse response =
                courseRecommendationService.recommend(request);

        Set<Long> selectedHotelIds = new HashSet<>();
        for (CourseOptionResponse option : response.getCourseOptions()) {
            assertEquals(11, option.getPlaceCount());
            assertEquals(3, option.getDays().size());

            Long optionHotelId = null;
            for (int dayIndex = 0; dayIndex < 2; dayIndex++) {
                CourseDayResponse day = option.getDays().get(dayIndex);
                assertEquals(4, day.getPlaces().size());
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
                assertEquals(
                        0,
                        day.getPlaces().get(day.getPlaces().size() - 1)
                                .getExpectedVisitMinutes()
                );
                assertTrue(Boolean.TRUE.equals(
                        day.getPlaces().get(day.getPlaces().size() - 1)
                                .getRouteEstimated()
                ));
            }

            CourseDayResponse finalDay = option.getDays().get(2);
            assertEquals(finalDate, finalDay.getVisitDate());
            assertEquals(3, finalDay.getPlaces().size());
            assertTrue(finalDay.getPlaces().stream()
                    .noneMatch(place -> "HOTEL".equals(place.getCategory())));
            assertTrue(!recommendationKeyPlaceIds(
                    option.getRecommendationKey()
            ).contains(optionHotelId));
            selectedHotelIds.add(optionHotelId);
        }
        assertEquals(3, selectedHotelIds.size());
    }

    @Test
    @DisplayName("숙소 출발은 20분 이내로 유지하며 도착만 30분까지 완화한다")
    void walkingHotelRelaxesUpToThirtyMinutes() {
        LocalDate firstDate = LocalDate.of(2026, 7, 20);
        LocalDate finalDate = LocalDate.of(2026, 7, 21);
        List<Long> firstDayOrdinaryIds = List.of(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L
        );
        Map<String, Double> pairMinutes = new LinkedHashMap<>();
        for (Long ordinaryId : firstDayOrdinaryIds) {
            pairMinutes.put(pairKey(ordinaryId, 100L), 25.0);
        }
        CourseRecommendationService service = actualWalkingService(
                pairMinutes,
                5.0
        );
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(301L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(13, 0))
                .hotelCandidates(List.of(
                        candidate(100L, "30분 이내 숙소", "HOTEL",
                                95.0, 37.60, 127.10)
                ))
                .dailyPlans(List.of(
                        DailyPlanRequest.builder()
                                .visitDate(firstDate)
                                .targetPlaceCount(3)
                                .categoryTargets(Map.of("TOUR", 3))
                                .placeCandidates(List.of(
                                        candidate(1L, "첫날 관광지 1", "TOUR", 95.0, 37.50, 127.00),
                                        candidate(2L, "첫날 관광지 2", "TOUR", 94.0, 37.51, 127.01),
                                        candidate(3L, "첫날 관광지 3", "TOUR", 93.0, 37.52, 127.02),
                                        candidate(4L, "첫날 관광지 4", "TOUR", 92.0, 37.53, 127.03),
                                        candidate(5L, "첫날 관광지 5", "TOUR", 91.0, 37.54, 127.04),
                                        candidate(6L, "첫날 관광지 6", "TOUR", 90.0, 37.55, 127.05),
                                        candidate(7L, "첫날 관광지 7", "TOUR", 89.0, 37.56, 127.06),
                                        candidate(8L, "첫날 관광지 8", "TOUR", 88.0, 37.57, 127.07),
                                        candidate(9L, "첫날 관광지 9", "TOUR", 87.0, 37.58, 127.08)
                                ))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(finalDate)
                                .targetPlaceCount(3)
                                .categoryTargets(Map.of("TOUR", 3))
                                .placeCandidates(List.of(
                                        candidate(11L, "마지막날 관광지 1", "TOUR", 95.0, 37.50, 127.00),
                                        candidate(12L, "마지막날 관광지 2", "TOUR", 94.0, 37.51, 127.01),
                                        candidate(13L, "마지막날 관광지 3", "TOUR", 93.0, 37.52, 127.02),
                                        candidate(14L, "마지막날 관광지 4", "TOUR", 92.0, 37.53, 127.03),
                                        candidate(15L, "마지막날 관광지 5", "TOUR", 91.0, 37.54, 127.04),
                                        candidate(16L, "마지막날 관광지 6", "TOUR", 90.0, 37.55, 127.05),
                                        candidate(17L, "마지막날 관광지 7", "TOUR", 89.0, 37.56, 127.06),
                                        candidate(18L, "마지막날 관광지 8", "TOUR", 88.0, 37.57, 127.07),
                                        candidate(19L, "마지막날 관광지 9", "TOUR", 87.0, 37.58, 127.08)
                                ))
                                .build()
                ))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        for (CourseOptionResponse option : response.getCourseOptions()) {
            assertTrue(Boolean.TRUE.equals(option.getHotelIncluded()));
            assertTrue(option.getHotelNotice() == null);
            CourseDayResponse firstDay = option.getDays().get(0);
            assertEquals("HOTEL", firstDay.getPlaces()
                    .get(firstDay.getPlaces().size() - 1)
                    .getCategory());
            assertEquals(25.0, firstDay.getPlaces()
                    .get(firstDay.getPlaces().size() - 1)
                    .getTravelTimeFromPreviousMinutes());
            assertTrue(option.getDays().get(1).getPlaces().stream()
                    .noneMatch(place -> "HOTEL".equals(place.getCategory())));
        }
    }

    @Test
    @DisplayName("DAY2 첫 장소까지 20분을 넘는 숙소는 도보 코스에서 선택하지 않는다")
    void walkingHotelDepartureOverTwentyMinutesIsRejected() {
        LocalDate firstDate = LocalDate.of(2026, 7, 20);
        LocalDate finalDate = LocalDate.of(2026, 7, 21);
        List<Long> firstDayOrdinaryIds = List.of(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L
        );
        List<Long> secondDayOrdinaryIds = List.of(
                11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L
        );
        Map<String, Double> pairMinutes = new LinkedHashMap<>();
        for (Long ordinaryId : firstDayOrdinaryIds) {
            pairMinutes.put(pairKey(ordinaryId, 100L), 10.0);
            pairMinutes.put(pairKey(ordinaryId, 101L), 10.0);
        }
        for (Long ordinaryId : secondDayOrdinaryIds) {
            pairMinutes.put(pairKey(ordinaryId, 100L), 21.0);
            pairMinutes.put(pairKey(ordinaryId, 101L), 10.0);
        }

        CourseRecommendationService service = actualWalkingService(
                pairMinutes,
                5.0
        );
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(303L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(13, 0))
                .hotelCandidates(List.of(
                        candidate(100L, "출발 21분 숙소", "HOTEL",
                                99.0, 37.60, 127.10),
                        candidate(101L, "출발 10분 숙소", "HOTEL",
                                80.0, 37.55, 127.05)
                ))
                .dailyPlans(List.of(
                        DailyPlanRequest.builder()
                                .visitDate(firstDate)
                                .targetPlaceCount(3)
                                .categoryTargets(Map.of("TOUR", 3))
                                .placeCandidates(List.of(
                                        candidate(1L, "첫날 관광지 1", "TOUR", 95.0, 37.50, 127.00),
                                        candidate(2L, "첫날 관광지 2", "TOUR", 94.0, 37.51, 127.01),
                                        candidate(3L, "첫날 관광지 3", "TOUR", 93.0, 37.52, 127.02),
                                        candidate(4L, "첫날 관광지 4", "TOUR", 92.0, 37.53, 127.03),
                                        candidate(5L, "첫날 관광지 5", "TOUR", 91.0, 37.54, 127.04),
                                        candidate(6L, "첫날 관광지 6", "TOUR", 90.0, 37.55, 127.05),
                                        candidate(7L, "첫날 관광지 7", "TOUR", 89.0, 37.56, 127.06),
                                        candidate(8L, "첫날 관광지 8", "TOUR", 88.0, 37.57, 127.07),
                                        candidate(9L, "첫날 관광지 9", "TOUR", 87.0, 37.58, 127.08)
                                ))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(finalDate)
                                .targetPlaceCount(3)
                                .categoryTargets(Map.of("TOUR", 3))
                                .placeCandidates(List.of(
                                        candidate(11L, "둘째날 관광지 1", "TOUR", 95.0, 37.50, 127.00),
                                        candidate(12L, "둘째날 관광지 2", "TOUR", 94.0, 37.51, 127.01),
                                        candidate(13L, "둘째날 관광지 3", "TOUR", 93.0, 37.52, 127.02),
                                        candidate(14L, "둘째날 관광지 4", "TOUR", 92.0, 37.53, 127.03),
                                        candidate(15L, "둘째날 관광지 5", "TOUR", 91.0, 37.54, 127.04),
                                        candidate(16L, "둘째날 관광지 6", "TOUR", 90.0, 37.55, 127.05),
                                        candidate(17L, "둘째날 관광지 7", "TOUR", 89.0, 37.56, 127.06),
                                        candidate(18L, "둘째날 관광지 8", "TOUR", 88.0, 37.57, 127.07),
                                        candidate(19L, "둘째날 관광지 9", "TOUR", 87.0, 37.58, 127.08)
                                ))
                                .build()
                ))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        for (CourseOptionResponse option : response.getCourseOptions()) {
            assertTrue(Boolean.TRUE.equals(option.getHotelIncluded()));
            CourseDayResponse firstDay = option.getDays().get(0);
            CourseDayResponse secondDay = option.getDays().get(1);
            Long hotelId = firstDay.getPlaces()
                    .get(firstDay.getPlaces().size() - 1)
                    .getPlaceId();
            Long secondDayFirstPlaceId = secondDay.getPlaces().get(0)
                    .getPlaceId();

            assertEquals(101L, hotelId);
            assertTrue(
                    pairMinutes.get(pairKey(
                            hotelId,
                            secondDayFirstPlaceId
                    )) <= 20.0
            );
        }
    }

    @Test
    @DisplayName("도보 30분 이내 숙소가 없으면 숙소를 제외하고 안내를 반환한다")
    void walkingHotelOverThirtyMinutesReturnsNotice() {
        LocalDate firstDate = LocalDate.of(2026, 7, 20);
        LocalDate finalDate = LocalDate.of(2026, 7, 21);
        List<Long> ordinaryIds = List.of(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L,
                11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L
        );
        Map<String, Double> pairMinutes = new LinkedHashMap<>();
        for (Long ordinaryId : ordinaryIds) {
            pairMinutes.put(pairKey(ordinaryId, 100L), 35.0);
        }
        CourseRecommendationService service = actualWalkingService(
                pairMinutes,
                5.0
        );
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(302L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(13, 0))
                .hotelCandidates(List.of(
                        candidate(100L, "너무 먼 숙소", "HOTEL",
                                95.0, 37.70, 127.20)
                ))
                .dailyPlans(List.of(
                        DailyPlanRequest.builder()
                                .visitDate(firstDate)
                                .targetPlaceCount(3)
                                .categoryTargets(Map.of("TOUR", 3))
                                .placeCandidates(List.of(
                                        candidate(1L, "첫날 관광지 1", "TOUR", 95.0, 37.50, 127.00),
                                        candidate(2L, "첫날 관광지 2", "TOUR", 94.0, 37.51, 127.01),
                                        candidate(3L, "첫날 관광지 3", "TOUR", 93.0, 37.52, 127.02),
                                        candidate(4L, "첫날 관광지 4", "TOUR", 92.0, 37.53, 127.03),
                                        candidate(5L, "첫날 관광지 5", "TOUR", 91.0, 37.54, 127.04),
                                        candidate(6L, "첫날 관광지 6", "TOUR", 90.0, 37.55, 127.05),
                                        candidate(7L, "첫날 관광지 7", "TOUR", 89.0, 37.56, 127.06),
                                        candidate(8L, "첫날 관광지 8", "TOUR", 88.0, 37.57, 127.07),
                                        candidate(9L, "첫날 관광지 9", "TOUR", 87.0, 37.58, 127.08)
                                ))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(finalDate)
                                .targetPlaceCount(3)
                                .categoryTargets(Map.of("TOUR", 3))
                                .placeCandidates(List.of(
                                        candidate(11L, "마지막날 관광지 1", "TOUR", 95.0, 37.50, 127.00),
                                        candidate(12L, "마지막날 관광지 2", "TOUR", 94.0, 37.51, 127.01),
                                        candidate(13L, "마지막날 관광지 3", "TOUR", 93.0, 37.52, 127.02),
                                        candidate(14L, "마지막날 관광지 4", "TOUR", 92.0, 37.53, 127.03),
                                        candidate(15L, "마지막날 관광지 5", "TOUR", 91.0, 37.54, 127.04),
                                        candidate(16L, "마지막날 관광지 6", "TOUR", 90.0, 37.55, 127.05),
                                        candidate(17L, "마지막날 관광지 7", "TOUR", 89.0, 37.56, 127.06),
                                        candidate(18L, "마지막날 관광지 8", "TOUR", 88.0, 37.57, 127.07),
                                        candidate(19L, "마지막날 관광지 9", "TOUR", 87.0, 37.58, 127.08)
                                ))
                                .build()
                ))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        for (CourseOptionResponse option : response.getCourseOptions()) {
            assertTrue(Boolean.FALSE.equals(option.getHotelIncluded()));
            assertTrue(option.getHotelNotice().contains(
                    "숙소 도착 30분·다음 DAY 출발 20분"
            ));
            assertTrue(option.getDays().stream()
                    .flatMap(day -> day.getPlaces().stream())
                    .noneMatch(place -> "HOTEL".equals(place.getCategory())));
        }
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
    @DisplayName("2일 도보 코스는 가능한 동안 DAY 간 일반 장소를 다시 사용하지 않는다")
    void walkingRecommendationAvoidsOrdinaryPlaceReuseAcrossDays() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(),
                10.0
        );
        List<PlaceCandidateDto> candidates = walkingTourCandidates(9);
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(116L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(
                        walkingTourPlan(
                                LocalDate.of(2026, 7, 24),
                                3,
                                candidates
                        ),
                        walkingTourPlan(
                                LocalDate.of(2026, 7, 25),
                                3,
                                candidates
                        )
                ))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        assertEquals(3, response.getOptionCount());
        for (CourseOptionResponse option : response.getCourseOptions()) {
            assertEquals(2, option.getDays().size());
            Set<Long> firstDayIds = ordinaryPlaceIds(
                    option.getDays().get(0)
            );
            Set<Long> secondDayIds = ordinaryPlaceIds(
                    option.getDays().get(1)
            );
            Set<Long> overlap = new HashSet<>(firstDayIds);
            overlap.retainAll(secondDayIds);

            assertEquals(3, firstDayIds.size());
            assertEquals(3, secondDayIds.size());
            assertTrue(overlap.isEmpty());
        }
    }

    @Test
    @DisplayName("서로 다른 코스는 날짜가 달라도 DAY 조합마다 중복을 제한한다")
    void walkingRecommendationLimitsOverlapAcrossDifferentOptionDays() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(),
                10.0
        );
        List<PlaceCandidateDto> candidates = walkingTourCandidates(24);
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(125L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(
                        walkingTourPlan(
                                LocalDate.of(2026, 7, 24),
                                3,
                                candidates
                        ),
                        walkingTourPlan(
                                LocalDate.of(2026, 7, 25),
                                3,
                                candidates
                        )
                ))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        assertEquals(3, response.getOptionCount());
        assertAllOptionDayPairsOverlapAtMost(response, 1);
    }

    @Test
    @DisplayName("두 DAY 모두 최소 3곳을 무중복으로 채울 수 없으면 잘못된 코스를 반환하지 않는다")
    void walkingRecommendationFailsBeforeCrossDayReuse() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(),
                10.0
        );
        List<PlaceCandidateDto> candidates = walkingTourCandidates(5);
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(117L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(
                        walkingTourPlan(
                                LocalDate.of(2026, 7, 24),
                                3,
                                candidates
                        ),
                        walkingTourPlan(
                                LocalDate.of(2026, 7, 25),
                                3,
                                candidates
                        )
                ))
                .build();

        assertThrows(IllegalStateException.class, () -> service.recommend(
                request
        ));
    }

    @Test
    @DisplayName("DAY 간 무중복을 위해 하루를 3곳 미만으로 줄이지 않는다")
    void walkingRecommendationNeverReturnsFewerThanThreePlaces() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(),
                10.0
        );
        List<PlaceCandidateDto> candidates = walkingTourCandidates(4);
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(118L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(
                        walkingTourPlan(
                                LocalDate.of(2026, 7, 24),
                                3,
                                candidates
                        ),
                        walkingTourPlan(
                                LocalDate.of(2026, 7, 25),
                                3,
                                candidates
                        )
                ))
                .build();

        assertThrows(IllegalStateException.class, () -> service.recommend(
                request
        ));
    }

    @Test
    @DisplayName("유효한 도보 장소 묶음이 하나뿐이면 같은 하루 코스를 복제하지 않는다")
    void walkingRecommendationDoesNotCloneOneClusterIntoThreeOptions() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(
                        pairKey(1L, 11L), 12.0,
                        pairKey(11L, 21L), 12.0
                ),
                120.0
        );
        List<PlaceCandidateDto> candidates = List.of(
                candidate(1L, "관광지 1", "TOUR", 99.0, 37.501, 127.001),
                candidate(2L, "관광지 2", "TOUR", 98.0, 37.502, 127.002),
                candidate(3L, "관광지 3", "TOUR", 97.0, 37.503, 127.003),
                candidate(4L, "관광지 4", "TOUR", 96.0, 37.504, 127.004),
                candidate(5L, "관광지 5", "TOUR", 95.0, 37.505, 127.005),
                candidate(6L, "관광지 6", "TOUR", 94.0, 37.506, 127.006),
                candidate(11L, "식당 1", "RESTAURANT", 93.0, 37.511, 127.011),
                candidate(12L, "식당 2", "RESTAURANT", 92.0, 37.512, 127.012),
                candidate(13L, "식당 3", "RESTAURANT", 91.0, 37.513, 127.013),
                candidate(21L, "카페 1", "CAFE", 90.0, 37.521, 127.021),
                candidate(22L, "카페 2", "CAFE", 89.0, 37.522, 127.022),
                candidate(23L, "카페 3", "CAFE", 88.0, 37.523, 127.023)
        );
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(124L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 27))
                        .targetPlaceCount(6)
                        .categoryTargets(Map.of(
                                "TOUR", 3,
                                "RESTAURANT", 2,
                                "CAFE", 1,
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
    @DisplayName("도보 후보가 부족하면 중복을 최대 3곳까지만 단계적으로 완화한다")
    void walkingOverlapRelaxationStopsAtThree() throws Exception {
        CourseRecommendationService service = actualWalkingService(
                Map.of(),
                10.0
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 28);

        CourseRecommendRequest threeOverlapRequest =
                CourseRecommendRequest.builder()
                        .resultId(125L)
                        .travelCode("ATLSP")
                        .transportMode(TransportMode.WALKING)
                        .dailyStartTime(LocalTime.of(11, 0))
                        .dailyPlans(List.of(walkingPackedPlan(
                                visitDate,
                                walkingPackedOverlapCandidates(
                                        0L,
                                        5,
                                        2
                                )
                        )))
                        .build();

        CourseRecommendResponse response =
                service.recommend(threeOverlapRequest);

        assertEquals(3, response.getOptionCount());
        assertDailyOverlapAtMost(response, 3);
        assertTrue(maximumFirstDayOptionOverlap(response) >= 2);

        java.lang.reflect.Field maximumOverlapField =
                CourseRecommendationService.class.getDeclaredField(
                        "MAX_WALKING_RELAXED_DAILY_OVERLAP_LIMIT"
                );
        maximumOverlapField.setAccessible(true);
        assertEquals(
                3,
                maximumOverlapField.getInt(null)
        );
    }

    @Test
    @DisplayName("확장 후보가 있으면 도보 재추천은 이전과 완전히 같은 코스를 반환하지 않는다")
    void recommendAgainOmitsExactDuplicateWhenExpandedCandidatesExist() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(),
                10.0
        );
        List<PlaceCandidateDto> candidates = walkingTourCandidates(18);
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(119L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(walkingTourPlan(
                        LocalDate.of(2026, 7, 24),
                        3,
                        candidates
                )))
                .build();

        CourseRecommendResponse first =
                service.recommend(request);
        assertEquals(3, first.getOptionCount());

        Set<String> previousKeys = first.getCourseOptions().stream()
                .map(CourseOptionResponse::getRecommendationKey)
                .collect(java.util.stream.Collectors.toSet());
        request.setExcludedRecommendationKeys(List.copyOf(previousKeys));
        request.setPreviouslyRecommendedPlaceIds(
                List.copyOf(ordinaryPlaceIds(first))
        );
        CourseRecommendResponse second =
                service.recommend(request);

        assertEquals(3, second.getOptionCount());
        assertTrue(second.getCourseOptions().stream()
                .map(CourseOptionResponse::getRecommendationKey)
                .noneMatch(previousKeys::contains));
        assertDailyOverlapAtMost(second, 1);
    }

    @Test
    @DisplayName("도보 장소 수를 줄여도 P형 식당·카페 카테고리를 유지한다")
    void walkingRecommendationReducesPlacesWithoutBreakingLimits() {
        Map<String, Double> pairMinutes = new LinkedHashMap<>();
        List<List<Long>> connectedClusters = List.of(
                List.of(1L, 11L, 2L, 21L),
                List.of(3L, 12L, 4L, 22L),
                List.of(5L, 13L, 6L, 23L)
        );
        for (List<Long> cluster : connectedClusters) {
            for (int index = 1; index < cluster.size(); index++) {
                pairMinutes.put(
                        pairKey(cluster.get(index - 1), cluster.get(index)),
                        16.0
                );
            }
        }
        CourseRecommendationService service = actualWalkingService(
                pairMinutes,
                120.0
        );
        LocalDate visitDate = LocalDate.of(2026, 7, 24);
        List<PlaceCandidateDto> candidates = List.of(
                candidate(1L, "관광지 1", "TOUR", 99.0, 37.501, 127.001),
                candidate(2L, "관광지 2", "TOUR", 98.0, 37.502, 127.002),
                candidate(3L, "관광지 3", "TOUR", 97.0, 37.503, 127.003),
                candidate(4L, "관광지 4", "TOUR", 96.0, 37.504, 127.004),
                candidate(5L, "관광지 5", "TOUR", 95.0, 37.505, 127.005),
                candidate(6L, "관광지 6", "TOUR", 94.0, 37.506, 127.006),
                candidate(11L, "식당 1", "RESTAURANT", 93.0, 37.511, 127.011),
                candidate(12L, "식당 2", "RESTAURANT", 92.0, 37.512, 127.012),
                candidate(13L, "식당 3", "RESTAURANT", 91.0, 37.513, 127.013),
                candidate(21L, "카페 1", "CAFE", 90.0, 37.521, 127.021),
                candidate(22L, "카페 2", "CAFE", 89.0, 37.522, 127.022),
                candidate(23L, "카페 3", "CAFE", 88.0, 37.523, 127.023)
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
                                "RESTAURANT", 3,
                                "CAFE", 3,
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
            assertEquals(2, countCategory(day, "TOUR"));
            assertEquals(1, countCategory(day, "RESTAURANT"));
            assertEquals(1, countCategory(day, "CAFE"));
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
                        pairKey(5L, 6L), 10.0,
                        pairKey(7L, 8L), 10.0,
                        pairKey(8L, 9L), 10.0,
                        pairKey(10L, 11L), 10.0,
                        pairKey(11L, 12L), 10.0
                ),
                120.0
        );
        List<PlaceCandidateDto> candidates = List.of(
                candidate(1L, "고득점 장소 1", "TOUR", 100.0, 37.50, 127.00),
                candidate(2L, "고득점 장소 2", "TOUR", 99.0, 37.51, 127.01),
                candidate(3L, "고득점 장소 3", "TOUR", 98.0, 37.52, 127.02),
                candidate(4L, "가까운 장소 4", "TOUR", 80.0, 37.53, 127.03),
                candidate(5L, "가까운 장소 5", "TOUR", 79.0, 37.54, 127.04),
                candidate(6L, "가까운 장소 6", "TOUR", 78.0, 37.55, 127.05),
                candidate(7L, "가까운 장소 7", "TOUR", 77.0, 37.56, 127.06),
                candidate(8L, "가까운 장소 8", "TOUR", 76.0, 37.57, 127.07),
                candidate(9L, "가까운 장소 9", "TOUR", 75.0, 37.58, 127.08),
                candidate(10L, "가까운 장소 10", "TOUR", 74.0, 37.59, 127.09),
                candidate(11L, "가까운 장소 11", "TOUR", 73.0, 37.60, 127.10),
                candidate(12L, "가까운 장소 12", "TOUR", 72.0, 37.61, 127.11)
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

        assertEquals(3, selectedIds.size());
        assertTrue(selectedIds.stream().noneMatch(
                placeId -> placeId <= 3L
        ));
        assertEquals(20.0, preference.getTotalTravelTimeMinutes(), 0.000001);
        assertWalkingLimits(preference.getDays().get(0));
    }

    @Test
    @DisplayName("도보 평균 15분 경로가 없으면 장소 수를 유지한 채 평균 18분 단계로 완화한다")
    void walkingRecommendationUsesEighteenMinuteTierBeforeReducingPlaces() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(
                        pairKey(1L, 2L), 17.0,
                        pairKey(2L, 3L), 17.0,
                        pairKey(4L, 5L), 17.0,
                        pairKey(5L, 6L), 17.0,
                        pairKey(7L, 8L), 17.0,
                        pairKey(8L, 9L), 17.0
                ),
                120.0
        );
        List<PlaceCandidateDto> candidates = List.of(
                candidate(1L, "도보 장소 1", "TOUR", 96.0, 37.50, 127.00),
                candidate(2L, "도보 장소 2", "TOUR", 95.0, 37.51, 127.01),
                candidate(3L, "도보 장소 3", "TOUR", 94.0, 37.52, 127.02),
                candidate(4L, "도보 장소 4", "TOUR", 93.0, 37.53, 127.03),
                candidate(5L, "도보 장소 5", "TOUR", 92.0, 37.54, 127.04),
                candidate(6L, "도보 장소 6", "TOUR", 91.0, 37.55, 127.05),
                candidate(7L, "도보 장소 7", "TOUR", 90.0, 37.56, 127.06),
                candidate(8L, "도보 장소 8", "TOUR", 89.0, 37.57, 127.07),
                candidate(9L, "도보 장소 9", "TOUR", 88.0, 37.58, 127.08)
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

        CourseOptionResponse preference = response.getCourseOptions().get(0);
        assertEquals(3, preference.getDays().get(0).getPlaces().size());
        assertEquals(
                34.0,
                preference.getTotalTravelTimeMinutes(),
                0.000001
        );
        for (CourseOptionResponse option : response.getCourseOptions()) {
            CourseDayResponse day = option.getDays().get(0);
            assertTrue(day.getPlaces().size() >= 2);
            assertTrue(day.getPlaces().size() <= 3);
            assertTrue(option.getTotalTravelTimeMinutes() <= 34.0);
            assertWalkingLimits(day);
        }
    }

    @Test
    @DisplayName("추정 경로는 통과하지만 실제 한 구간이 20분을 넘으면 장소를 줄여 복구한다")
    void walkingRecommendationRepairsActualOverLimitCandidate() {
        CourseRecommendationService service =
                walkingServiceWithActualOverrides(
                        Map.of(pairKey(1L, 2L), 21.7),
                        10.0,
                        10.0
                );
        List<PlaceCandidateDto> candidates = walkingTourCandidates(24);
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(126L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(walkingTourPlan(
                        LocalDate.of(2026, 7, 27),
                        4,
                        candidates
                )))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        assertEquals(3, response.getOptionCount());
        assertEquals(3, response.getCourseOptions().size());
        for (CourseOptionResponse option : response.getCourseOptions()) {
            for (CourseDayResponse day : option.getDays()) {
                assertEquals(4, day.getPlaces().size());
                assertTrue(!Boolean.TRUE.equals(
                        day.getPlaceCountAdjusted()
                ));
                assertWalkingLimits(day);
            }
        }
        assertAllOptionDayPairsOverlapAtMost(response, 1);
    }

    @Test
    @DisplayName("일부 실제 3곳 경로가 20분을 넘으면 다른 후보를 먼저 찾아 장소 수를 유지한다")
    void walkingRecommendationTriesOtherActualCandidatesBeforeReducing() {
        CourseRecommendationService service =
                walkingServiceWithActualOverrides(
                        Map.of(pairKey(1L, 2L), 20.9),
                        10.0,
                        10.0
                );
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(127L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(walkingTourPlan(
                        LocalDate.of(2026, 7, 28),
                        3,
                        walkingTourCandidates(12)
                )))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        assertEquals(3, response.getOptionCount());
        assertEquals(3, response.getCourseOptions().size());
        for (CourseOptionResponse option : response.getCourseOptions()) {
            for (CourseDayResponse day : option.getDays()) {
                assertEquals(3, day.getPlaces().size());
                assertTrue(!Boolean.TRUE.equals(
                        day.getPlaceCountAdjusted()
                ));
                assertWalkingLimits(day);
            }
        }
        assertAllOptionDayPairsOverlapAtMost(response, 1);
    }

    @Test
    @DisplayName("확장 후보도 실제 3곳 경로가 불가능할 때만 2곳과 조정 안내를 반환한다")
    void walkingRecommendationReturnsTwoPlacesWithNoticeOnlyAsLastResort() {
        CourseRecommendationService service =
                walkingServiceWithActualDailyPlaceLimit(2);
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(129L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(walkingTourPlan(
                        LocalDate.of(2026, 7, 29),
                        3,
                        walkingTourCandidates(12)
                )))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        assertEquals(3, response.getOptionCount());
        for (CourseOptionResponse option : response.getCourseOptions()) {
            CourseDayResponse day = option.getDays().get(0);
            assertEquals(2, day.getPlaces().size());
            assertTrue(Boolean.TRUE.equals(day.getPlaceCountAdjusted()));
            assertEquals(3, day.getRequestedPlaceCount());
            assertEquals(2, day.getActualPlaceCount());
            assertTrue(day.getAdjustmentNotice() != null
                    && !day.getAdjustmentNotice().isBlank());
            assertWalkingLimits(day);
        }
        assertAllOptionDayPairsOverlapAtMost(response, 1);
    }

    @Test
    @DisplayName("실제 제한 실패가 여러 DAY에 남으면 한 곳씩 줄여 세 코스를 다시 고른다")
    void walkingRecommendationRegeneratesAfterReducingFailedDays() {
        CourseRecommendationService service =
                walkingServiceWithActualDailyPlaceLimit(5);
        List<PlaceCandidateDto> candidates = packedCandidates();
        Map<String, Integer> categoryTargets = Map.of(
                "TOUR", 6,
                "RESTAURANT", 3,
                "CAFE", 3,
                "HOTEL", 0
        );
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(128L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(
                        DailyPlanRequest.builder()
                                .visitDate(LocalDate.of(2026, 7, 28))
                                .targetPlaceCount(6)
                                .categoryTargets(categoryTargets)
                                .placeCandidates(candidates)
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(LocalDate.of(2026, 7, 29))
                                .targetPlaceCount(6)
                                .categoryTargets(categoryTargets)
                                .placeCandidates(candidates)
                                .build()
                ))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        assertEquals(3, response.getOptionCount());
        assertEquals(3, response.getCourseOptions().size());
        for (CourseOptionResponse option : response.getCourseOptions()) {
            assertEquals(2, option.getDays().size());
            for (CourseDayResponse day : option.getDays()) {
                assertEquals(6, day.getRequestedPlaceCount());
                assertEquals(5, day.getActualPlaceCount());
                assertTrue(Boolean.TRUE.equals(
                        day.getPlaceCountAdjusted()
                ));
                assertEquals(5, day.getPlaces().size());
                assertEquals(2, countCategory(day, "TOUR"));
                assertEquals(2, countCategory(day, "RESTAURANT"));
                assertEquals(1, countCategory(day, "CAFE"));
                assertWalkingLimits(day);
            }
        }
        assertAllOptionDayPairsOverlapAtMost(response, 1);
    }

    @Test
    @DisplayName("ORS 실패 예상값도 도보 상한 안이면 예상 배지와 함께 반환한다")
    void walkingRecommendationUsesSafeEstimatedFallback() {
        CourseRecommendationService service = estimatedWalkingService(
                Map.of(),
                10.0
        );
        List<PlaceCandidateDto> candidates = walkingTourCandidates(9);
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(122L)
                .travelCode("ATLSP")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(11, 0))
                .dailyPlans(List.of(DailyPlanRequest.builder()
                        .visitDate(LocalDate.of(2026, 7, 24))
                        .targetPlaceCount(3)
                        .categoryTargets(Map.of("TOUR", 3))
                        .placeCandidates(candidates)
                        .build()))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        assertTrue(response.getEstimatedTravelTimes());
        for (CourseOptionResponse option : response.getCourseOptions()) {
            CourseDayResponse day = option.getDays().get(0);
            assertTrue(option.getEstimatedTravelTimes());
            for (int index = 1; index < day.getPlaces().size(); index++) {
                assertTrue(Boolean.TRUE.equals(
                        day.getPlaces().get(index).getRouteEstimated()
                ));
                assertTrue(day.getPlaces().get(index)
                        .getTravelTimeFromPreviousMinutes() <= 20.0);
            }
        }
    }

    @Test
    @DisplayName("도보 구간당 20분 제한은 장소 수를 줄여도 절대 완화하지 않는다")
    void walkingRecommendationNeverRelaxesTwentyMinuteLegLimit() {
        CourseRecommendationService service = actualWalkingService(
                Map.of(
                        pairKey(1L, 2L), 21.0,
                        pairKey(2L, 3L), 21.0,
                        pairKey(1L, 3L), 21.0
                ),
                120.0
        );
        List<PlaceCandidateDto> candidates = List.of(
                candidate(1L, "도보 장소 1", "TOUR", 96.0, 37.50, 127.00),
                candidate(2L, "도보 장소 2", "TOUR", 95.0, 37.51, 127.01),
                candidate(3L, "도보 장소 3", "TOUR", 94.0, 37.52, 127.02)
        );

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(115L)
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

    private DailyPlanRequest walkingTourPlan(
            LocalDate visitDate,
            int targetPlaceCount,
            List<PlaceCandidateDto> candidates
    ) {
        return DailyPlanRequest.builder()
                .visitDate(visitDate)
                .targetPlaceCount(targetPlaceCount)
                .categoryTargets(Map.of(
                        "TOUR", targetPlaceCount,
                        "RESTAURANT", 0,
                        "CAFE", 0,
                        "HOTEL", 0
                ))
                .placeCandidates(candidates)
                .build();
    }

    private DailyPlanRequest walkingPackedPlan(
            LocalDate visitDate,
            List<PlaceCandidateDto> candidates
    ) {
        return DailyPlanRequest.builder()
                .visitDate(visitDate)
                .targetPlaceCount(6)
                .categoryTargets(Map.of(
                        "TOUR", 3,
                        "RESTAURANT", 2,
                        "CAFE", 1,
                        "HOTEL", 0
                ))
                .placeCandidates(candidates)
                .build();
    }

    private List<PlaceCandidateDto> walkingPackedOverlapCandidates(
            long idOffset,
            int tourCount,
            int cafeCount
    ) {
        List<PlaceCandidateDto> candidates = new ArrayList<>();
        for (long index = 1; index <= tourCount; index++) {
            candidates.add(candidate(
                    idOffset + index,
                    "도보 관광지 " + index,
                    "TOUR",
                    100.0 - index,
                    37.50 + index * 0.0001,
                    127.00 + index * 0.0001
            ));
        }
        for (long index = 1; index <= 3; index++) {
            candidates.add(candidate(
                    idOffset + 10 + index,
                    "도보 식당 " + index,
                    "RESTAURANT",
                    90.0 - index,
                    37.51 + index * 0.0001,
                    127.01 + index * 0.0001
            ));
        }
        for (long index = 1; index <= cafeCount; index++) {
            candidates.add(candidate(
                    idOffset + 20 + index,
                    "도보 카페 " + index,
                    "CAFE",
                    80.0 - index,
                    37.52 + index * 0.0001,
                    127.02 + index * 0.0001
            ));
        }
        return candidates;
    }

    private List<PlaceCandidateDto> walkingTourCandidates(int count) {
        List<PlaceCandidateDto> candidates = new ArrayList<>();
        for (long placeId = 1; placeId <= count; placeId++) {
            candidates.add(candidate(
                    placeId,
                    "도보 장소 " + placeId,
                    "TOUR",
                    100.0 - placeId,
                    37.50 + placeId * 0.001,
                    127.00 + placeId * 0.001
            ));
        }
        return candidates;
    }

    private long countCategory(CourseDayResponse day, String category) {
        return day.getPlaces().stream()
                .filter(place -> category.equals(place.getCategory()))
                .count();
    }

    private Set<Long> ordinaryPlaceIds(CourseDayResponse day) {
        return day.getPlaces().stream()
                .filter(place -> !"HOTEL".equals(place.getCategory()))
                .map(place -> place.getPlaceId())
                .collect(java.util.stream.Collectors.toSet());
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

    private int maximumFirstDayOptionOverlap(
            CourseRecommendResponse response
    ) {
        List<Set<Long>> optionPlaceIds = response.getCourseOptions().stream()
                .map(option -> ordinaryPlaceIds(
                        option.getDays().get(0)
                ))
                .toList();
        int maximumOverlap = 0;
        for (int left = 0; left < optionPlaceIds.size(); left++) {
            for (int right = left + 1;
                 right < optionPlaceIds.size();
                 right++) {
                Set<Long> overlap = new HashSet<>(
                        optionPlaceIds.get(left)
                );
                overlap.retainAll(optionPlaceIds.get(right));
                maximumOverlap = Math.max(
                        maximumOverlap,
                        overlap.size()
                );
            }
        }
        return maximumOverlap;
    }

    private Set<Long> recommendationKeyPlaceIds(
            String recommendationKey
    ) {
        Set<Long> placeIds = new HashSet<>();
        for (String token : recommendationKey.split(",")) {
            int separatorIndex = token.lastIndexOf(':');
            if (separatorIndex < 0
                    || separatorIndex == token.length() - 1) {
                continue;
            }
            placeIds.add(Long.parseLong(
                    token.substring(separatorIndex + 1)
            ));
        }
        return placeIds;
    }

    private void assertAllOptionDayPairsOverlapAtMost(
            CourseRecommendResponse response,
            int limit
    ) {
        List<CourseOptionResponse> options = response.getCourseOptions();
        for (int leftOption = 0;
             leftOption < options.size();
             leftOption++) {
            for (int rightOption = leftOption + 1;
                 rightOption < options.size();
                 rightOption++) {
                for (CourseDayResponse leftDay
                        : options.get(leftOption).getDays()) {
                    for (CourseDayResponse rightDay
                            : options.get(rightOption).getDays()) {
                        Set<Long> leftIds = ordinaryPlaceIds(leftDay);
                        Set<Long> rightIds = ordinaryPlaceIds(rightDay);
                        Set<Long> overlap = new HashSet<>(leftIds);
                        overlap.retainAll(rightIds);

                        assertTrue(!leftIds.equals(rightIds));
                        assertTrue(overlap.size() <= limit);
                    }
                }
            }
        }
    }

    private CourseRecommendationService walkingServiceWithActualOverrides(
            Map<String, Double> actualPairMinutes,
            double estimatedDefaultMinutes,
            double actualDefaultMinutes
    ) {
        DistanceService distanceService = new DistanceService() {
            private RouteMatrix createMatrix(
                    List<PlaceCandidateDto> places,
                    boolean actual
            ) {
                int size = places.size();
                double[][] distances = new double[size][size];
                double[][] travelTimes = new double[size][size];
                for (int from = 0; from < size; from++) {
                    for (int to = 0; to < size; to++) {
                        if (from == to) {
                            continue;
                        }
                        double minutes = actual
                                ? actualPairMinutes.getOrDefault(
                                pairKey(
                                        places.get(from).getPlaceId(),
                                        places.get(to).getPlaceId()
                                ),
                                actualDefaultMinutes
                        )
                                : estimatedDefaultMinutes;
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
                return createMatrix(places, false);
            }

            @Override
            public RouteMatrix calculateRouteMatrix(
                    List<PlaceCandidateDto> places,
                    TransportMode transportMode
            ) {
                return createMatrix(places, true);
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

    /**
     * 후보 추정값은 항상 통과시키고, 실제 DAY가 기준 개수보다 많을 때만
     * 21.7분을 반환해 전체 재선택 fallback을 재현한다.
     */
    private CourseRecommendationService
    walkingServiceWithActualDailyPlaceLimit(
            int maximumSafePlacesPerDay
    ) {
        DistanceService distanceService = new DistanceService() {
            private RouteMatrix createMatrix(
                    List<PlaceCandidateDto> places,
                    double minutes
            ) {
                int size = places.size();
                double[][] distances = new double[size][size];
                double[][] travelTimes = new double[size][size];
                for (int from = 0; from < size; from++) {
                    for (int to = 0; to < size; to++) {
                        if (from == to) {
                            continue;
                        }
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
                return createMatrix(places, 10.0);
            }

            @Override
            public RouteMatrix calculateRouteMatrix(
                    List<PlaceCandidateDto> places,
                    TransportMode transportMode
            ) {
                return createMatrix(
                        places,
                        places.size() <= maximumSafePlacesPerDay
                                ? 10.0
                                : 21.7
                );
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

    @Test
    @DisplayName("이전 추천과 같은 4곳 구성뿐이어도 각 DAY를 다르게 줄여 새 도보 3코스를 만든다")
    void walkingPreviousCompositionCreatesReducedNovelOptions() {
        LocalDate firstDate = LocalDate.of(2026, 7, 27);
        LocalDate secondDate = LocalDate.of(2026, 7, 28);
        CourseRecommendationService service = actualWalkingService(
                Map.of(),
                8.0
        );
        String previousKey = "WALKING:"
                + firstDate + ":1," + firstDate + ":2,"
                + firstDate + ":3," + firstDate + ":4,"
                + secondDate + ":11," + secondDate + ":12,"
                + secondDate + ":13," + secondDate + ":14";
        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .resultId(430L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.WALKING)
                .dailyStartTime(LocalTime.of(13, 0))
                .excludedRecommendationKeys(List.of(previousKey))
                .previouslyRecommendedPlaceIds(List.of(
                        1L, 2L, 3L, 4L,
                        11L, 12L, 13L, 14L
                ))
                .dailyPlans(List.of(
                        DailyPlanRequest.builder()
                                .visitDate(firstDate)
                                .targetPlaceCount(4)
                                .categoryTargets(categoryTargets())
                                .placeCandidates(List.of(
                                        candidate(1L, "첫날 관광 1", "TOUR", 95.0, 37.50, 127.00),
                                        candidate(2L, "첫날 관광 2", "TOUR", 94.0, 37.51, 127.01),
                                        candidate(3L, "첫날 식당", "RESTAURANT", 93.0, 37.52, 127.02),
                                        candidate(4L, "첫날 카페", "CAFE", 92.0, 37.53, 127.03)
                                ))
                                .build(),
                        DailyPlanRequest.builder()
                                .visitDate(secondDate)
                                .targetPlaceCount(4)
                                .categoryTargets(categoryTargets())
                                .placeCandidates(List.of(
                                        candidate(11L, "둘째날 관광 1", "TOUR", 95.0, 37.54, 127.04),
                                        candidate(12L, "둘째날 관광 2", "TOUR", 94.0, 37.55, 127.05),
                                        candidate(13L, "둘째날 식당", "RESTAURANT", 93.0, 37.56, 127.06),
                                        candidate(14L, "둘째날 카페", "CAFE", 92.0, 37.57, 127.07)
                                ))
                                .build()
                ))
                .build();

        CourseRecommendResponse response = service.recommend(request);

        assertEquals(3, response.getCourseOptions().size());
        Set<String> recommendationKeys = new HashSet<>();
        for (CourseOptionResponse option : response.getCourseOptions()) {
            assertTrue(!previousKey.equals(option.getRecommendationKey()));
            recommendationKeys.add(option.getRecommendationKey());
            assertEquals(2, option.getDays().size());
            assertTrue(option.getDays().stream()
                    .anyMatch(day -> day.getPlaces().size() == 3));
            option.getDays().forEach(day -> {
                assertTrue(day.getPlaces().size() >= 3);
                assertTrue(day.getPlaces().size() <= 4);
                assertWalkingLimits(day);
            });
        }
        assertEquals(3, recommendationKeys.size());
        for (int left = 0; left < response.getCourseOptions().size(); left++) {
            for (int right = left + 1;
                 right < response.getCourseOptions().size(); right++) {
                for (CourseDayResponse leftDay
                        : response.getCourseOptions().get(left).getDays()) {
                    Set<Long> leftIds = leftDay.getPlaces().stream()
                            .map(place -> place.getPlaceId())
                            .collect(java.util.stream.Collectors.toSet());
                    for (CourseDayResponse rightDay
                            : response.getCourseOptions().get(right).getDays()) {
                        Set<Long> rightIds = rightDay.getPlaces().stream()
                                .map(place -> place.getPlaceId())
                                .collect(java.util.stream.Collectors.toSet());
                        assertTrue(!leftIds.equals(rightIds));
                    }
                }
            }
        }
    }

    private CourseRecommendationService actualWalkingService(
            Map<String, Double> pairMinutes,
            double defaultMinutes
    ) {
        return walkingService(pairMinutes, defaultMinutes, false);
    }

    private CourseRecommendationService estimatedWalkingService(
            Map<String, Double> pairMinutes,
            double defaultMinutes
    ) {
        return walkingService(pairMinutes, defaultMinutes, true);
    }

    private CourseRecommendationService walkingService(
            Map<String, Double> pairMinutes,
            double defaultMinutes,
            boolean estimated
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
                        estimated
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

    private PlaceCandidateDto candidateWithRegion(
            Long placeId,
            String placeName,
            String category,
            Double score,
            Double latitude,
            Double longitude,
            String region
    ) {
        PlaceCandidateDto candidate = candidate(
                placeId,
                placeName,
                category,
                score,
                latitude,
                longitude
        );
        candidate.setRegion(region);
        candidate.setAddress("서울특별시 " + region + " " + placeName);
        candidate.setRoadAddress("서울특별시 " + region + " 테스트로 " + placeId);
        return candidate;
    }

}
