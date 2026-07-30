package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.dto.response.PlaceAlternativeResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceCandidatePoolResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationListResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.exception.InvalidTravelCodeException;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PlaceRecommendationService {

    // 요청에서 개수를 생략했을 때 반환할 대표 추천 장소 수
    private static final int DEFAULT_LIMIT = 10;

    // 대표 추천 장소 하나당 함께 반환할 대체 후보 수
    private static final int DEFAULT_ALTERNATIVE_LIMIT = 3;

    private static final int MAX_LIMIT = 50;
    private static final int MAX_LIMIT_PER_CATEGORY = 50;

    private static final Pattern DISTRICT_PATTERN = Pattern.compile("([가-힣]+구)");
    private static final Pattern NEIGHBORHOOD_PATTERN = Pattern.compile("([가-힣0-9]+동)");

    // 여행 코드 한 글자와 장소의 성향 태그가 일치할 때 얻는 점수
    // 5개 성향이 모두 일치하면 18점 * 5개 = 90점이다.
    private static final double CODE_MATCH_SCORE = 18.0;
    private static final double MAX_COMPANION_BONUS = 10.0;
    private static final double DISPLAY_SCORE_MIN = 70.0;
    private static final double DISPLAY_SCORE_MAX = 95.0;
    private static final double EQUAL_SCORE_DISPLAY_VALUE = 85.0;
    private static final int HOTEL_CANDIDATE_LIMIT = 6;
    private static final int PREFERRED_REGION_LIMIT = 5;
    private static final int REGION_SCORE_PLACE_LIMIT = 3;
    private static final double REGION_CANDIDATE_COUNT_BONUS = 1.0;
    private static final int MAX_COURSE_CANDIDATE_TOTAL = 90;
    private static final int MAX_COURSE_TRAVEL_DAYS = 5;
    private static final List<Double> PREFERRED_REGION_BONUSES =
            List.of(8.0, 6.0, 4.0, 2.0, 1.0);

    private static final String TOUR = "TOUR";
    private static final String RESTAURANT = "RESTAURANT";
    private static final String CAFE = "CAFE";
    private static final String HOTEL = "HOTEL";

    // 기존 추천 API가 기본 장소 종류를 고르게 포함하도록 사용하는 카테고리 목록이다.
    private static final List<String> SUPPORTED_CATEGORIES = List.of(
            TOUR,
            RESTAURANT,
            CAFE,
            HOTEL
    );

    private static final List<String> COURSE_CANDIDATE_CATEGORIES = List.of(
            TOUR,
            RESTAURANT,
            CAFE
    );

    private final PlaceRepository placeRepository;

    public PlaceRecommendationService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    /**
     * 저장된 추천 코스 상세에서도 최초 추천과 같은 장소별 표시 점수를 복원한다.
     *
     * <p>추천 후보를 만들 때와 동일하게 서울 전체 활성 장소를 취향 코드와 동행
     * 유형으로 점수화한 뒤 70~95점으로 보정한다. 따라서 별도 점수 컬럼을
     * 추가하지 않아도 기존 추천 이력까지 같은 계산 기준으로 표시할 수 있다.</p>
     */
    public Map<Long, Double> findDisplayScores(
            String travelCode,
            String companionType,
            Collection<Long> placeIds
    ) {
        if (placeIds == null || placeIds.isEmpty()) {
            return Map.of();
        }

        Set<Long> requestedPlaceIds = placeIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(placeId -> placeId > 0)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (requestedPlaceIds.isEmpty()) {
            return Map.of();
        }

        String normalizedTravelCode = normalizeTravelCode(travelCode);
        String normalizedCompanionType = normalizeCompanionType(companionType);
        Map<Long, Double> scoresByPlaceId = new LinkedHashMap<>();

        for (ScoredPlace scoredPlace : scoreAllPlaces(
                null,
                normalizedTravelCode,
                normalizedCompanionType
        )) {
            Long placeId = scoredPlace.place().getPlaceId();
            if (requestedPlaceIds.contains(placeId)) {
                scoresByPlaceId.put(placeId, scoredPlace.score());
            }
        }

        return Map.copyOf(scoresByPlaceId);
    }

    /**
     * 코스 생성 서비스가 HTTP나 프론트 전달 없이 직접 호출하는 후보 조회 진입점이다.
     *
     * <p>P형은 TOUR 24, RESTAURANT 16, CAFE 8의 유효 후보 48개를,
     * R형은 TOUR 16, RESTAURANT 8, CAFE 8의 유효 후보 32개를 목표로 한다.
     * 좌표나 카테고리가 잘못된 후보가 섞일 수 있으므로 각각 최대 54개와 36개의
     * 상위 후보를 검토한다.</p>
     *
     * <p>기존 장소 ID는 기본 후보에서 우선 제외하되 후보 부족 시 코스 생성 서비스가
     * 감점 재사용할 수 있도록 fallbackCandidatesByCategory에 별도로 반환한다.
     * 장소마다 alternativeCandidates를 중첩하지 않고 같은 카테고리 후보 풀을
     * 한 번만 제공한다.</p>
     */
    public PlaceCandidatePoolResponse recommendCandidatePool(
            String travelCode,
            String region,
            String scheduleType,
            String companionType,
            Collection<Long> excludedPlaceIds
    ) {
        return recommendCandidatePoolInternal(
                travelCode,
                region,
                scheduleType,
                companionType,
                excludedPlaceIds,
                null
        );
    }

    /**
     * 여행 일수와 세 가지 추천 옵션에 필요한 장소 수를 기준으로 후보 풀을 확장한다.
     *
     * <p>하루 기준 P형 30개(15·10·5), R형 20개(10·5·5)를 여행
     * 일수만큼 확보해 도보 20분 이내 지역 묶음을 충분히 탐색한다. 전체 후보는
     * 최대 90개로 제한하며, 상위 구는 가산점만 적용하므로 해당 지역 후보가
     * 부족하면 서울 전체 후보가 자동으로 채워진다.</p>
     */
    public PlaceCandidatePoolResponse recommendCandidatePool(
            String travelCode,
            String region,
            String scheduleType,
            String companionType,
            Collection<Long> excludedPlaceIds,
            int travelDays
    ) {
        if (travelDays < 1 || travelDays > MAX_COURSE_TRAVEL_DAYS) {
            throw new IllegalArgumentException(
                    "여행 기간은 1일 이상 "
                            + MAX_COURSE_TRAVEL_DAYS
                            + "일 이하여야 합니다."
            );
        }
        return recommendCandidatePoolInternal(
                travelCode,
                region,
                scheduleType,
                companionType,
                excludedPlaceIds,
                travelDays
        );
    }

    private PlaceCandidatePoolResponse recommendCandidatePoolInternal(
            String travelCode,
            String region,
            String scheduleType,
            String companionType,
            Collection<Long> excludedPlaceIds,
            Integer travelDays
    ) {
        String normalizedTravelCode = normalizeTravelCode(travelCode);
        String normalizedRegion = normalizeRegion(region);
        String normalizedCompanionType =
                normalizeCompanionType(companionType);
        String normalizedScheduleType = normalizeScheduleType(
                scheduleType,
                normalizedTravelCode
        );
        Set<Long> normalizedExcludedPlaceIds =
                normalizeExcludedPlaceIds(excludedPlaceIds);
        CandidatePoolPolicy policy =
                travelDays == null
                        ? candidatePoolPolicy(normalizedScheduleType)
                        : expandedCandidatePoolPolicy(
                                normalizedScheduleType,
                                travelDays
                        );

        List<ScoredPlace> scoredPlaces = scoreAllPlaces(
                normalizedRegion,
                normalizedTravelCode,
                normalizedCompanionType
        );
        List<String> preferredRegions =
                rankPreferredRegions(scoredPlaces);
        List<ScoredPlace> courseRankedPlaces =
                sortForCoursePool(scoredPlaces, preferredRegions);

        Map<String, List<PlaceRecommendationResponse>>
                candidatesByCategory = selectCandidatePools(
                courseRankedPlaces,
                policy.targetCounts(),
                policy.maxLookupCounts(),
                normalizedExcludedPlaceIds,
                false
        );

        Map<String, List<PlaceRecommendationResponse>>
                fallbackCandidatesByCategory = selectCandidatePools(
                courseRankedPlaces,
                policy.targetCounts(),
                policy.maxLookupCounts(),
                normalizedExcludedPlaceIds,
                true
        );

        List<PlaceRecommendationResponse> hotelCandidates =
                selectHotelCandidates(
                        courseRankedPlaces,
                        normalizedExcludedPlaceIds,
                        false
                );
        List<PlaceRecommendationResponse> fallbackHotelCandidates =
                selectHotelCandidates(
                        courseRankedPlaces,
                        normalizedExcludedPlaceIds,
                        true
                );

        return new PlaceCandidatePoolResponse(
                normalizedTravelCode,
                normalizedScheduleType,
                normalizedCompanionType,
                preferredRegions,
                policy.targetCandidateCount(),
                policy.maxLookupCount(),
                candidatesByCategory,
                fallbackCandidatesByCategory,
                hotelCandidates,
                fallbackHotelCandidates
        );
    }

    /**
     * 추천 API의 전체 처리 흐름이다.
     *
     * 1. 여행 코드를 검사하고 대문자로 통일한다.
     * 2. 활성화된 장소만 DB에서 조회한다.
     * 3. 각 장소의 취향 점수 + 평점 보너스 + 리뷰 보너스를 계산한다.
     * 4. 점수가 높은 순서대로 대표 추천 장소를 선택한다.
     * 5. 대표 장소마다 겹치지 않는 대체 후보를 붙인다.
     */
    public PlaceRecommendationListResponse recommend(String travelCode, Integer limit, Integer alternativeLimit) {
        return recommend(travelCode, null, limit, null, alternativeLimit, null);
    }

    /**
     * 기존 화면과 테스트가 지역 및 후보 규모를 지정해 호출하는 호환 추천 진입점이다.
     * limitPerCategory가 있으면 각 카테고리에서 해당 개수만큼 반환하고,
     * 없으면 기존 호환 방식대로 전체 limit 안에서 카테고리 대표를 우선 확보한다.
     */
    public PlaceRecommendationListResponse recommend(
            String travelCode,
            String region,
            Integer limit,
            Integer limitPerCategory,
            Integer alternativeLimit
    ) {
        return recommend(
                travelCode,
                region,
                limit,
                limitPerCategory,
                alternativeLimit,
                null
        );
    }

    /**
     * 여행 코드와 동행 유형을 함께 반영하는 코스 후보용 추천 진입점이다.
     * 동행 유형은 장소 순위에만 영향을 주며 최종 화면 점수는 70~95 범위로 보정한다.
     */
    public PlaceRecommendationListResponse recommend(
            String travelCode,
            String region,
            Integer limit,
            Integer limitPerCategory,
            Integer alternativeLimit,
            String companionType
    ) {
        // 예: " atbsp " -> "ATBSP"
        String normalizedTravelCode = normalizeTravelCode(travelCode);
        String normalizedRegion = normalizeRegion(region);
        String normalizedCompanionType = normalizeCompanionType(companionType);

        // limit 값이 없거나 0 이하이면 기본값을 사용하고, 최대 50개까지만 허용한다.
        int resolvedLimit = resolveLimit(limit, DEFAULT_LIMIT);
        Integer resolvedLimitPerCategory = resolveLimitPerCategory(limitPerCategory);
        int resolvedAlternativeLimit = resolveLimit(alternativeLimit, DEFAULT_ALTERNATIVE_LIMIT);

        List<ScoredPlace> scoredPlaces = scoreAllPlaces(
                normalizedRegion,
                normalizedTravelCode,
                normalizedCompanionType
        );
        List<String> preferredRegions =
                rankPreferredRegions(scoredPlaces);

        List<ScoredPlace> recommended = resolvedLimitPerCategory == null
                ? selectCategoryBalancedPlaces(scoredPlaces, resolvedLimit)
                : selectPlacesPerCategory(scoredPlaces, resolvedLimitPerCategory);

        // 대표 추천 장소가 다른 장소의 대체 후보로 다시 들어가지 않도록 ID를 모아 둔다.
        Set<Long> unavailableAlternativeIds = recommended.stream()
                .map(scoredPlace -> scoredPlace.place().getPlaceId())
                .collect(Collectors.toSet());

        // 한 대체 장소가 여러 대표 장소에 반복되지 않도록 앞에서 사용한 ID를 계속 누적한다.
        List<PlaceRecommendationResponse> recommendedPlaces = new ArrayList<>();
        for (ScoredPlace scoredPlace : recommended) {
            PlaceRecommendationResponse response = toRecommendationResponse(
                    scoredPlace,
                    scoredPlaces,
                    unavailableAlternativeIds,
                    resolvedAlternativeLimit
            );
            recommendedPlaces.add(response);
            response.getAlternativeCandidates().forEach(
                    alternative -> unavailableAlternativeIds.add(alternative.getPlaceId())
            );
        }

        // 최종 JSON: { "travelCode": "ATBSP", "recommendedPlaces": [...] }
        return new PlaceRecommendationListResponse(
                normalizedTravelCode,
                preferredRegions,
                recommendedPlaces
        );
    }

    /**
     * 구별 추천점수 상위 세 곳의 합을 중심으로 상위 다섯 구를 정한다.
     *
     * <p>숙소는 실제 관광 취향 지역을 왜곡할 수 있어 제외하고, 후보 수는 같은
     * 점수대에서만 순서를 보조하도록 작은 가산점으로 제한한다.</p>
     */
    private List<String> rankPreferredRegions(
            List<ScoredPlace> scoredPlaces
    ) {
        Map<String, List<Double>> scoresByRegion =
                new LinkedHashMap<>();
        for (ScoredPlace scoredPlace : scoredPlaces) {
            if (sameCategory(scoredPlace.place().getCategory(), HOTEL)
                    || !isUsableCourseCandidate(scoredPlace.place())) {
                continue;
            }
            String region = normalizeDistrict(
                    scoredPlace.place().getRegion()
            );
            if (region == null) {
                continue;
            }
            scoresByRegion
                    .computeIfAbsent(region, ignored -> new ArrayList<>())
                    .add(scoredPlace.score());
        }

        return scoresByRegion.entrySet().stream()
                .map(entry -> {
                    List<Double> scores = entry.getValue().stream()
                            .sorted(Comparator.reverseOrder())
                            .toList();
                    double topScoreSum = scores.stream()
                            .limit(REGION_SCORE_PLACE_LIMIT)
                            .mapToDouble(Double::doubleValue)
                            .sum();
                    double countBonus = Math.min(
                            scores.size(),
                            PREFERRED_REGION_LIMIT
                    ) * REGION_CANDIDATE_COUNT_BONUS;
                    return new RegionScore(
                            entry.getKey(),
                            topScoreSum + countBonus,
                            scores.get(0),
                            scores.size()
                    );
                })
                .sorted(Comparator
                        .comparingDouble(RegionScore::score)
                        .reversed()
                        .thenComparing(
                                RegionScore::bestPlaceScore,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                RegionScore::candidateCount,
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                RegionScore::region,
                                Comparator.naturalOrder()
                        ))
                .limit(PREFERRED_REGION_LIMIT)
                .map(RegionScore::region)
                .toList();
    }

    /**
     * 상위 구는 순위별 가산점만 받고, 나머지 서울 후보도 같은 목록에 그대로 남긴다.
     * 따라서 상위 구의 카테고리 후보가 부족하면 별도 오류 없이 서울 전체 후보로
     * 자동 완화된다.
     */
    private List<ScoredPlace> sortForCoursePool(
            List<ScoredPlace> scoredPlaces,
            List<String> preferredRegions
    ) {
        return scoredPlaces.stream()
                .sorted(Comparator
                        .comparingDouble(
                                (ScoredPlace scoredPlace) ->
                                        scoredPlace.score()
                                                + preferredRegionBonus(
                                                scoredPlace.place(),
                                                preferredRegions
                                        )
                        )
                        .reversed()
                        .thenComparing(scoreComparator()))
                .toList();
    }

    private double preferredRegionBonus(
            Place place,
            List<String> preferredRegions
    ) {
        String region = normalizeDistrict(place.getRegion());
        if (region == null) {
            return 0.0;
        }
        int regionIndex = preferredRegions.indexOf(region);
        return regionIndex >= 0
                && regionIndex < PREFERRED_REGION_BONUSES.size()
                ? PREFERRED_REGION_BONUSES.get(regionIndex)
                : 0.0;
    }

    private String normalizeDistrict(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = DISTRICT_PATTERN.matcher(value.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 활성 장소를 사용자 취향과 동행 유형으로 점수화하고 표시 점수 70~95로 보정한다.
     * 추천 API와 코스용 후보 조회가 같은 점수 기준을 사용하도록 한 곳에 모아 둔다.
     */
    private List<ScoredPlace> scoreAllPlaces(
            String region,
            String travelCode,
            String companionType
    ) {
        List<ScoredPlace> rawScoredPlaces = findActivePlaces(region)
                .stream()
                .map(place -> scorePlace(
                        place,
                        travelCode,
                        companionType
                ))
                .filter(scoredPlace ->
                        scoredPlace.preferenceScore() > 0)
                .toList();

        return normalizeDisplayScores(rawScoredPlaces)
                .stream()
                .sorted(scoreComparator())
                .toList();
    }

    /**
     * 일정 유형에 맞는 TOUR·RESTAURANT·CAFE 후보 풀을 만든다.
     *
     * <p>fallback=false이면 제외 ID가 아닌 장소를, fallback=true이면 제외 ID인
     * 장소만 반환한다. 최대 검토 개수를 먼저 적용하고 그 안에서 사용할 수 없는 장소를
     * 제거하므로 필터 탈락을 고려한 54/36 상한이 실제로 작동한다.</p>
     */
    private Map<String, List<PlaceRecommendationResponse>>
    selectCandidatePools(
            List<ScoredPlace> scoredPlaces,
            Map<String, Integer> targetCounts,
            Map<String, Integer> maxLookupCounts,
            Set<Long> excludedPlaceIds,
            boolean fallback
    ) {
        Map<String, List<PlaceRecommendationResponse>> pools =
                new LinkedHashMap<>();

        for (String category : COURSE_CANDIDATE_CATEGORIES) {
            int targetCount = targetCounts.getOrDefault(category, 0);
            int maxLookupCount =
                    maxLookupCounts.getOrDefault(category, targetCount);

            List<PlaceRecommendationResponse> candidates =
                    scoredPlaces.stream()
                            .filter(scoredPlace ->
                                    sameCategory(
                                            scoredPlace.place().getCategory(),
                                            category
                                    ))
                            .filter(scoredPlace ->
                                    fallback
                                            == excludedPlaceIds.contains(
                                            scoredPlace.place().getPlaceId()
                                    ))
                            .limit(maxLookupCount)
                            .filter(scoredPlace ->
                                    isUsableCourseCandidate(
                                            scoredPlace.place()
                                    ))
                            .limit(targetCount)
                            .map(this::toPoolCandidateResponse)
                            .toList();

            pools.put(category, candidates);
        }

        return pools;
    }

    private List<PlaceRecommendationResponse> selectHotelCandidates(
            List<ScoredPlace> scoredPlaces,
            Set<Long> excludedPlaceIds,
            boolean fallback
    ) {
        return scoredPlaces.stream()
                .filter(scoredPlace ->
                        sameCategory(
                                scoredPlace.place().getCategory(),
                                HOTEL
                        ))
                .filter(scoredPlace ->
                        fallback
                                == excludedPlaceIds.contains(
                                scoredPlace.place().getPlaceId()
                        ))
                .filter(scoredPlace ->
                        isUsableCourseCandidate(scoredPlace.place()))
                .limit(HOTEL_CANDIDATE_LIMIT)
                .map(this::toPoolCandidateResponse)
                .toList();
    }

    /**
     * 코스 후보에서는 장소별 대체 후보를 만들지 않는다.
     * 같은 카테고리의 나머지 후보 풀이 대체 후보 역할을 한다.
     */
    private PlaceRecommendationResponse toPoolCandidateResponse(
            ScoredPlace scoredPlace
    ) {
        return new PlaceRecommendationResponse(
                scoredPlace.place(),
                scoredPlace.score(),
                List.of()
        );
    }

    private boolean isUsableCourseCandidate(Place place) {
        if (place == null || place.getPlaceId() == null) {
            return false;
        }
        if (!SUPPORTED_CATEGORIES.contains(normalizeCategory(
                place.getCategory()
        ))) {
            return false;
        }
        return isValidLatitude(place.getLatitude())
                && isValidLongitude(place.getLongitude());
    }

    private boolean isValidLatitude(Double latitude) {
        return latitude != null
                && Double.isFinite(latitude)
                && latitude >= -90.0
                && latitude <= 90.0;
    }

    private boolean isValidLongitude(Double longitude) {
        return longitude != null
                && Double.isFinite(longitude)
                && longitude >= -180.0
                && longitude <= 180.0;
    }

    /**
     * 대표 추천 장소 하나에 대체 장소 목록을 붙인다.
     */
    private PlaceRecommendationResponse toRecommendationResponse(
            ScoredPlace recommendedPlace,
            List<ScoredPlace> scoredPlaces,
            Set<Long> unavailableAlternativeIds,
            int alternativeLimit
    ) {
        List<PlaceAlternativeResponse> alternatives = scoredPlaces.stream()

                // 자기 자신은 자신의 대체 후보가 될 수 없다.
                .filter(candidate -> !candidate.place().getPlaceId().equals(recommendedPlace.place().getPlaceId()))

                // 이미 대표 추천 목록에 들어간 장소도 대체 후보에서는 제외한다.
                .filter(candidate -> !unavailableAlternativeIds.contains(candidate.place().getPlaceId()))

                // 코스 교체 후에도 식당이 관광지로 바뀌지 않도록 같은 기본 카테고리만 남긴다.
                .filter(candidate -> isAlternativeFor(recommendedPlace.place(), candidate.place()))

                // 겹치는 테마 수 -> 추천 점수 -> placeId 순서로 대체 후보를 정렬한다.
                .sorted(alternativeComparator(recommendedPlace.place()))
                .limit(alternativeLimit)
                .map(candidate -> new PlaceAlternativeResponse(candidate.place(), candidate.score()))
                .toList();

        return new PlaceRecommendationResponse(
                recommendedPlace.place(),
                recommendedPlace.score(),
                alternatives
        );
    }

    /**
     * 장소 하나의 최종 추천 점수를 계산한다.
     *
     * 현재 점수 구성:
     * - 여행 코드 일치 점수: 최대 90점 (18점 * 5글자)
     * - 평점 보너스: 최대 6점
     * - 리뷰 보너스: 최대 4점
     * - 동행 유형 보너스: 최대 10점
     * - 정렬용 원점수: 최대 110점
     *
     * <p>정렬용 원점수는 그대로 순위 결정에 사용하고, API로 반환할 때만
     * 후보 풀의 최저·최고 점수를 기준으로 70~95 범위에 선형 변환한다.</p>
     */
    private ScoredPlace scorePlace(
            Place place,
            String travelCode,
            String companionType
    ) {
        double preferenceScore = calculatePreferenceScore(place, travelCode);
        double score = preferenceScore;

        // 취향 코드 점수 외에 장소 자체의 신뢰도 지표를 보너스로 더한다.
        score += calculateRatingBonus(place);
        score += calculateReviewBonus(place);
        score += calculateCompanionBonus(place, companionType);

        // 이 값은 정렬용 원점수이며 화면 반환 직전에 70~95 범위로 보정한다.
        return new ScoredPlace(
                place,
                score,
                preferenceScore
        );
    }

    /**
     * 후보 풀의 최소·최대 원점수를 70~95에 선형 대응한다.
     * 단조 증가 변환이므로 기존 취향·동행 가중치로 정한 순서는 바뀌지 않는다.
     */
    private List<ScoredPlace> normalizeDisplayScores(
            List<ScoredPlace> rawScoredPlaces
    ) {
        if (rawScoredPlaces.isEmpty()) {
            return List.of();
        }

        double minimum = rawScoredPlaces.stream()
                .mapToDouble(ScoredPlace::score)
                .min()
                .orElse(0.0);
        double maximum = rawScoredPlaces.stream()
                .mapToDouble(ScoredPlace::score)
                .max()
                .orElse(0.0);

        return rawScoredPlaces.stream()
                .map(scoredPlace -> new ScoredPlace(
                        scoredPlace.place(),
                        normalizeDisplayScore(
                                scoredPlace.score(),
                                minimum,
                                maximum
                        ),
                        scoredPlace.preferenceScore()
                ))
                .toList();
    }

    private double normalizeDisplayScore(
            double rawScore,
            double minimum,
            double maximum
    ) {
        if (Math.abs(maximum - minimum) < 0.000000001) {
            return EQUAL_SCORE_DISPLAY_VALUE;
        }
        double normalized = DISPLAY_SCORE_MIN
                + (rawScore - minimum)
                / (maximum - minimum)
                * (DISPLAY_SCORE_MAX - DISPLAY_SCORE_MIN);
        return roundOneDecimal(Math.max(
                DISPLAY_SCORE_MIN,
                Math.min(DISPLAY_SCORE_MAX, normalized)
        ));
    }

    /**
     * 동행 유형별로 실제 서비스 테마와 장소 성향 태그를 가중한다.
     * 여러 조건이 겹쳐도 취향 코드보다 영향이 커지지 않도록 최대 10점으로 제한한다.
     */
    private double calculateCompanionBonus(
            Place place,
            String companionType
    ) {
        if (companionType == null) {
            return 0.0;
        }

        double bonus = switch (companionType) {
            case "SOLO" ->
                    yesBonus(place.getTagStable(), 3.0)
                            + yesBonus(place.getTagRelax(), 3.0)
                            + yesBonus(place.getThemeNatureHangangYn(), 2.0)
                            + yesBonus(place.getThemeCafeTourYn(), 1.0)
                            + yesBonus(place.getThemePalaceCultureYn(), 1.0);
            case "COUPLE" ->
                    yesBonus(place.getThemeDateYn(), 7.0)
                            + yesBonus(place.getThemeCafeTourYn(), 1.5)
                            + yesBonus(place.getThemeNightViewYn(), 1.5)
                            + yesBonus(place.getThemeNatureHangangYn(), 1.0);
            case "FRIENDS" ->
                    yesBonus(place.getThemeShoppingHotplaceYn(), 4.0)
                            + yesBonus(place.getThemeFoodTourYn(), 3.0)
                            + yesBonus(place.getThemeNightViewYn(), 2.0)
                            + yesBonus(place.getTagDopamine(), 2.0);
            case "FAMILY" ->
                    yesBonus(place.getTagStable(), 4.0)
                            + yesBonus(place.getThemePalaceCultureYn(), 3.0)
                            + yesBonus(place.getThemeNatureHangangYn(), 2.0)
                            + yesBonus(place.getTagRelax(), 1.0);
            default -> 0.0;
        };

        return Math.min(MAX_COMPANION_BONUS, bonus);
    }

    private double yesBonus(String value, double bonus) {
        return isYes(value) ? bonus : 0.0;
    }

    /**
     * 위치별 의미가 확정된 5글자 코드와 장소 태그를 비교한다.
     * 1=A/H, 2=T/M, 3=L/B, 4=S/D, 5=P/R 순서이며 각 위치는 최대 18점이다.
     */
    private double calculatePreferenceScore(Place place, String travelCode) {
        return calculateCodeScore(place, travelCode.charAt(0))
                + calculateCodeScore(place, travelCode.charAt(1))
                + calculateCodeScore(place, travelCode.charAt(2))
                + calculateCodeScore(place, travelCode.charAt(3))
                + calculateCodeScore(place, travelCode.charAt(4));
    }

    /**
     * 여행 코드 한 글자를 장소의 TAG_*_YN 값과 비교한다.
     * 해당 태그가 "Y"이면 18점, "N" 또는 null이면 0점이다.
     *
     * 현재 코드와 DB 태그 연결:
     * A -> TAG_DOPAMINE : 활동적인 장소
     * H -> TAG_RELAX    : 휴식·힐링 장소
     * T -> TAG_HISTORY  : 역사·전통 장소
     * M -> TAG_MODERN   : 현대적인 장소
     * B -> TAG_BUDGET   : 가성비 장소
     * L -> TAG_LUXURY   : 고급·럭셔리 장소
     * S -> TAG_STABLE   : 안정적인 장소
     * D -> TAG_DOPAMINE : 자극적인 장소
     * P -> TAG_PACKED   : 빽빽한 일정에 맞는 장소
     * R -> TAG_RELAX    : 여유로운 일정에 맞는 장소
     *
     * A와 D는 TAG_DOPAMINE을, H와 R은 TAG_RELAX을 함께 사용한다.
     * normalizeTravelCode에서 각 위치의 허용 문자를 먼저 검증한 뒤 이 메서드가 호출된다.
     */
    private double calculateCodeScore(Place place, char code) {
        return switch (code) {
            case 'A' -> yesScore(place.getTagDopamine());
            case 'H' -> yesScore(place.getTagRelax());
            case 'T' -> yesScore(place.getTagHistory());
            case 'M' -> yesScore(place.getTagModern());
            case 'B' -> yesScore(place.getTagBudget());
            case 'L' -> yesScore(place.getTagLuxury());
            case 'S' -> yesScore(place.getTagStable());
            case 'D' -> yesScore(place.getTagDopamine());
            case 'P' -> yesScore(place.getTagPacked());
            case 'R' -> yesScore(place.getTagRelax());
            default -> 0.0;
        };
    }

    /**
     * 대체 후보 자격을 검사한다. 대체 후에도 하루 카테고리 목표가 유지되어야 하므로
     * TOUR·RESTAURANT·CAFE·HOTEL 기본 카테고리가 같은 장소만 허용한다.
     */
    private boolean isAlternativeFor(Place basePlace, Place candidatePlace) {
        return sameCategory(basePlace.getCategory(), candidatePlace.getCategory());
    }

    /**
     * 카테고리별 최고점 장소를 먼저 확보하고 나머지를 전체 점수 순으로 채운다.
     * 최종 응답은 다시 점수순으로 정렬하므로 recommendedPlaces의 순서 의미도 유지된다.
     */
    private List<ScoredPlace> selectCategoryBalancedPlaces(List<ScoredPlace> scoredPlaces, int limit) {
        if (limit <= 0 || scoredPlaces.isEmpty()) {
            return List.of();
        }

        List<ScoredPlace> selected = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();

        List<ScoredPlace> categoryLeaders = SUPPORTED_CATEGORIES.stream()
                .map(category -> scoredPlaces.stream()
                        .filter(candidate -> sameCategory(candidate.place().getCategory(), category))
                        .findFirst()
                        .orElse(null))
                .filter(candidate -> candidate != null)
                .sorted(scoreComparator())
                .toList();

        for (ScoredPlace candidate : categoryLeaders) {
            if (selected.size() >= limit) break;
            addIfAbsent(selected, selectedIds, candidate);
        }

        for (ScoredPlace candidate : scoredPlaces) {
            if (selected.size() >= limit) break;
            addIfAbsent(selected, selectedIds, candidate);
        }

        return selected.stream()
                .sorted(scoreComparator())
                .toList();
    }

    /** 각 기본 카테고리에서 점수가 높은 후보를 동일한 상한으로 선별한다. */
    private List<ScoredPlace> selectPlacesPerCategory(List<ScoredPlace> scoredPlaces, int limitPerCategory) {
        return SUPPORTED_CATEGORIES.stream()
                .flatMap(category -> scoredPlaces.stream()
                        .filter(candidate -> sameCategory(candidate.place().getCategory(), category))
                        .limit(limitPerCategory))
                .sorted(scoreComparator())
                .toList();
    }

    private List<Place> findActivePlaces(String region) {
        if (region == null) {
            return placeRepository.findByIsActive("Y");
        }
        return placeRepository.findByRegionContainingAndIsActive(region, "Y");
    }

    private void addIfAbsent(List<ScoredPlace> selected, Set<Long> selectedIds, ScoredPlace candidate) {
        if (selectedIds.add(candidate.place().getPlaceId())) {
            selected.add(candidate);
        }
    }

    /**
     * 전체 추천 후보의 정렬 기준이다.
     * 1순위 추천 점수 내림차순
     * 2순위 평점 내림차순
     * 3순위 리뷰 수 내림차순
     * 4순위 placeId 오름차순
     *
     * 마지막에 placeId를 비교하므로 점수, 평점, 리뷰 수가 모두 같아도
     * 실행할 때마다 결과 순서가 달라지지 않는다.
     */
    private Comparator<ScoredPlace> scoreComparator() {
        return Comparator.comparing(ScoredPlace::score, Comparator.reverseOrder())
                .thenComparing(scoredPlace -> nullToZero(scoredPlace.place().getRating()), Comparator.reverseOrder())
                .thenComparing(scoredPlace -> nullToZero(scoredPlace.place().getReviewCount()), Comparator.reverseOrder())
                .thenComparing(scoredPlace -> scoredPlace.place().getPlaceId());
    }

    /**
     * 대체 후보의 정렬 기준이다.
     * 1순위 대표 장소와 겹치는 테마 수 내림차순
     * 2순위 추천 점수 내림차순
     * 3순위 placeId 오름차순
     */
    private Comparator<ScoredPlace> alternativeComparator(Place basePlace) {
        return Comparator.<ScoredPlace>comparingInt(candidate -> countThemeOverlap(basePlace, candidate.place()))
                .reversed()
                .thenComparing(ScoredPlace::score, Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.place().getPlaceId());
    }

    /**
     * 두 장소가 공통으로 가진 서비스 테마의 개수를 계산한다.
     * 예를 들어 두 장소 모두 데이트=Y, 야경=Y라면 결과는 2이다.
     */
    private int countThemeOverlap(Place left, Place right) {
        int count = 0;
        if (bothYes(left.getThemePalaceCultureYn(), right.getThemePalaceCultureYn())) count++;
        if (bothYes(left.getThemeNatureHangangYn(), right.getThemeNatureHangangYn())) count++;
        if (bothYes(left.getThemeDateYn(), right.getThemeDateYn())) count++;
        if (bothYes(left.getThemeFoodTourYn(), right.getThemeFoodTourYn())) count++;
        if (bothYes(left.getThemeCafeTourYn(), right.getThemeCafeTourYn())) count++;
        if (bothYes(left.getThemeShoppingHotplaceYn(), right.getThemeShoppingHotplaceYn())) count++;
        if (bothYes(left.getThemeNightViewYn(), right.getThemeNightViewYn())) count++;
        if (bothYes(left.getThemeHotelStayYn(), right.getThemeHotelStayYn())) count++;
        return count;
    }

    // DB 값이 "Y"일 때만 코드 일치 점수 18점을 반환한다.
    private double yesScore(String value) {
        return isYes(value) ? CODE_MATCH_SCORE : 0.0;
    }

    /**
     * 평점(0~5점)을 추천 점수 0~6점으로 환산한다.
     * 예: 평점 4.7 -> 4.7 / 5.0 * 6.0 = 5.64점
     */
    private double calculateRatingBonus(Place place) {
        return nullToZero(place.getRating()) / 5.0 * 6.0;
    }

    /**
     * 리뷰 수를 로그로 줄여서 0~4점의 보너스로 환산한다.
     * 리뷰 수가 아주 많더라도 최대 4점까지만 부여한다.
     * 로그를 사용하는 이유는 리뷰 10개와 100개의 차이보다
     * 리뷰 10,000개와 10,090개의 차이를 작게 반영하기 위해서다.
     */
    private double calculateReviewBonus(Place place) {
        int reviewCount = nullToZero(place.getReviewCount());
        return Math.min(4.0, Math.log10(reviewCount + 1.0) * 1.7);
    }

    /**
     * 입력된 여행 코드의 공백을 제거하고 대문자로 통일한다.
     * 각 위치는 팀에서 확정한 A/H, T/M, L/B, S/D, P/R 중 하나여야 한다.
     */
    private String normalizeTravelCode(String travelCode) {
        if (travelCode == null || travelCode.isBlank()) {
            throw new InvalidTravelCodeException("여행 유형 코드는 필수입니다.");
        }

        String normalized = travelCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 5) {
            throw new InvalidTravelCodeException("여행 유형 코드는 5글자여야 합니다.");
        }

        if (!normalized.matches("[AH][TM][LB][SD][PR]")) {
            throw new InvalidTravelCodeException(
                    "여행 유형 코드는 A/H, T/M, L/B, S/D, P/R 순서여야 합니다."
            );
        }

        return normalized;
    }

    /** 설문·화면에서 사용하는 동행 유형 영문값과 한글 별칭을 네 가지 표준값으로 맞춘다. */
    private String normalizeCompanionType(String companionType) {
        if (companionType == null || companionType.isBlank()) {
            return null;
        }

        String normalized = companionType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SOLO", "ALONE", "혼자" -> "SOLO";
            case "COUPLE", "연인", "커플" -> "COUPLE";
            case "FRIENDS", "FRIEND", "친구" -> "FRIENDS";
            case "FAMILY", "가족" -> "FAMILY";
            default -> throw new IllegalArgumentException(
                    "동행 유형은 SOLO, COUPLE, FRIENDS, FAMILY 중 하나여야 합니다."
            );
        };
    }

    /**
     * 일정 유형이 생략되면 여행 코드의 마지막 글자를 사용하고,
     * 둘 다 전달된 경우 서로 같은지 검사한다.
     */
    private String normalizeScheduleType(
            String scheduleType,
            String travelCode
    ) {
        String codeScheduleType =
                String.valueOf(travelCode.charAt(4));
        if (scheduleType == null || scheduleType.isBlank()) {
            return codeScheduleType;
        }

        String normalized =
                scheduleType.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("P") && !normalized.equals("R")) {
            throw new IllegalArgumentException(
                    "일정 유형은 P 또는 R이어야 합니다."
            );
        }
        if (!normalized.equals(codeScheduleType)) {
            throw new IllegalArgumentException(
                    "일정 유형은 여행 코드의 마지막 글자와 같아야 합니다."
            );
        }
        return normalized;
    }

    private Set<Long> normalizeExcludedPlaceIds(
            Collection<Long> excludedPlaceIds
    ) {
        if (excludedPlaceIds == null || excludedPlaceIds.isEmpty()) {
            return Set.of();
        }
        return excludedPlaceIds.stream()
                .filter(placeId -> placeId != null && placeId > 0)
                .collect(Collectors.toUnmodifiableSet());
    }

    private CandidatePoolPolicy candidatePoolPolicy(
            String scheduleType
    ) {
        if ("P".equals(scheduleType)) {
            return new CandidatePoolPolicy(
                    48,
                    54,
                    Map.of(
                            TOUR, 24,
                            RESTAURANT, 16,
                            CAFE, 8
                    ),
                    Map.of(
                            TOUR, 27,
                            RESTAURANT, 18,
                            CAFE, 9
                    )
            );
        }
        return new CandidatePoolPolicy(
                32,
                36,
                Map.of(
                        TOUR, 16,
                        RESTAURANT, 8,
                        CAFE, 8
                ),
                Map.of(
                        TOUR, 18,
                        RESTAURANT, 9,
                        CAFE, 9
                )
        );
    }

    /**
     * 하루 기본 후보 수를 여행 일수만큼 확장한다.
     * 3일 이상 일정은 행렬·탐색 비용이 급증하지 않도록 총 90개 안에서 비율을
     * 유지해 축소한다.
     */
    private CandidatePoolPolicy expandedCandidatePoolPolicy(
            String scheduleType,
            int travelDays
    ) {
        Map<String, Integer> finalDailyTargets = "P".equals(scheduleType)
                ? Map.of(TOUR, 3, RESTAURANT, 2, CAFE, 1)
                : Map.of(TOUR, 2, RESTAURANT, 1, CAFE, 1);
        Map<String, Integer> baseDailyCandidateTargets =
                "P".equals(scheduleType)
                        ? Map.of(TOUR, 15, RESTAURANT, 10, CAFE, 5)
                        : Map.of(TOUR, 10, RESTAURANT, 5, CAFE, 5);
        Map<String, Integer> rawTargets = new LinkedHashMap<>();
        for (String category : COURSE_CANDIDATE_CATEGORIES) {
            int expandedTarget =
                    baseDailyCandidateTargets.get(category) * travelDays;
            rawTargets.put(category, expandedTarget);
        }

        int rawTotal = rawTargets.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        double scale = rawTotal > MAX_COURSE_CANDIDATE_TOTAL
                ? (double) MAX_COURSE_CANDIDATE_TOTAL / rawTotal
                : 1.0;

        Map<String, Integer> targetCounts = new LinkedHashMap<>();
        for (String category : COURSE_CANDIDATE_CATEGORIES) {
            int minimum = finalDailyTargets.get(category);
            int scaledTarget = Math.max(
                    minimum,
                    (int) Math.floor(rawTargets.get(category) * scale)
            );
            targetCounts.put(category, scaledTarget);
        }

        Map<String, Integer> maxLookupCounts = new LinkedHashMap<>();
        targetCounts.forEach((category, target) ->
                maxLookupCounts.put(
                        category,
                        target + Math.max(3, (int) Math.ceil(target * 0.15))
                ));

        int targetCandidateCount = targetCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int maxLookupCount = maxLookupCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        return new CandidatePoolPolicy(
                targetCandidateCount,
                maxLookupCount,
                Map.copyOf(targetCounts),
                Map.copyOf(maxLookupCounts)
        );
    }

    private String normalizeCategory(String category) {
        if (category == null) {
            return "";
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }

    // 요청 개수가 비어 있거나 0 이하이면 기본값, 50보다 크면 50을 반환한다.
    private int resolveLimit(Integer value, int defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private Integer resolveLimitPerCategory(Integer value) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            return 1;
        }
        return Math.min(value, MAX_LIMIT_PER_CATEGORY);
    }

    /** 서울 전체 선택은 필터링하지 않고, 구·동처럼 구체적인 지역만 DB 조회에 적용한다. */
    private String normalizeRegion(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }

        String normalized = region.trim();
        String compact = normalized.replaceAll("\\s+", "");
        if (compact.equals("서울")
                || compact.equals("서울특별시")
                || compact.equals("서울전체")
                || compact.equals("전체")) {
            return null;
        }

        String localArea = compact
                .replaceFirst("^서울특별시", "")
                .replaceFirst("^서울시", "")
                .replaceFirst("^서울", "");

        Matcher districtMatcher = DISTRICT_PATTERN.matcher(localArea);
        if (districtMatcher.find()) {
            return districtMatcher.group(1);
        }

        Matcher neighborhoodMatcher = NEIGHBORHOOD_PATTERN.matcher(localArea);
        if (neighborhoodMatcher.find()) {
            return neighborhoodMatcher.group(1);
        }
        return normalized;
    }

    // 두 값이 모두 "Y"인지 확인한다. 테마 중복 개수를 셀 때 사용한다.
    private boolean bothYes(String left, String right) {
        return isYes(left) && isYes(right);
    }

    // 대소문자를 구분하지 않고 DB의 Y/N 값이 "Y"인지 확인한다.
    private boolean isYes(String value) {
        return "Y".equalsIgnoreCase(value);
    }

    private boolean sameCategory(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    // 평점이 null이면 계산 중 오류가 나지 않도록 0.0으로 바꾼다.
    private double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }

    // 리뷰 수가 null이면 계산 중 오류가 나지 않도록 0으로 바꾼다.
    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    // 소수점 첫째 자리까지 반올림한다. 예: 81.18 -> 81.2
    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // 장소와 계산된 점수를 함께 전달하기 위한 서비스 내부 전용 자료형이다.
    // DB 테이블이나 API JSON에는 ScoredPlace라는 이름으로 노출되지 않는다.
    private record ScoredPlace(Place place, Double score, Double preferenceScore) {
    }

    private record RegionScore(
            String region,
            Double score,
            Double bestPlaceScore,
            Integer candidateCount
    ) {
    }

    private record CandidatePoolPolicy(
            int targetCandidateCount,
            int maxLookupCount,
            Map<String, Integer> targetCounts,
            Map<String, Integer> maxLookupCounts
    ) {
    }
}
