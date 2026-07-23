package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.DailyPlanRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptionResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.service.DistanceService.RouteMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** 날짜별 후보 풀에서 서로 다른 세 가지 추천 코스를 순차 생성한다. */
@Service
public class CourseRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(
            CourseRecommendationService.class
    );
    private static final List<String> SUPPORTED_CATEGORIES = List.of(
            "TOUR",
            "RESTAURANT",
            "CAFE",
            "HOTEL"
    );
    private static final int MAX_EXCLUDED_RECOMMENDATIONS = 60;
    private static final int MAX_PREVIOUSLY_RECOMMENDED_PLACES = 500;
    private static final int PACKED_DAILY_OVERLAP_LIMIT = 2;
    private static final int RELAXED_DAILY_OVERLAP_LIMIT = 1;
    private static final double PREVIOUSLY_RECOMMENDED_PENALTY = 20.0;
    private static final double NEW_PLACE_BONUS = 5.0;
    private static final double DISPLAY_SCORE_MIN = 70.0;
    private static final double DISPLAY_SCORE_MAX = 95.0;
    private static final double EQUAL_SCORE_DISPLAY_VALUE = 85.0;
    private static final double EPSILON = 0.000000001;

    private final CourseOptimizationService courseOptimizationService;
    private final DistanceService distanceService;

    public CourseRecommendationService(
            CourseOptimizationService courseOptimizationService,
            DistanceService distanceService
    ) {
        this.courseOptimizationService = courseOptimizationService;
        this.distanceService = distanceService;
    }

    /**
     * 후보 전체 조합을 만들지 않고 취향·이동 최소·균형 코스를 하나씩 순차 생성한다.
     *
     * <p>날짜별 후보의 좌표 기반 추정 행렬을 한 번만 만든다. 두 번째와 세 번째 옵션은
     * 앞 옵션에서 쓰지 않은 장소를 먼저 선택하고, 재추천은 직전 세 옵션에 등장한 장소를
     * 먼저 완전히 제외한다. 해당 카테고리 후보가 모자랄 때만 제한을 단계적으로 풀어
     * 가장 적게 겹치는 장소를 재사용한다.</p>
     */
    public CourseRecommendResponse recommend(CourseRecommendRequest request) {
        ValidatedRecommendation validated = validateAndPrepare(request);
        Map<LocalDate, DailyRouteContext> routeContexts =
                createDailyRouteContexts(
                        validated.dailyPlans(),
                        validated.transportMode()
                );
        int dailyOverlapLimit = resolveDailyOverlapLimit(
                request.getTravelCode(),
                validated.dailyPlans()
        );

        List<CourseOptionResponse> courseOptions = new ArrayList<>();
        List<Map<LocalDate, Set<Long>>> generatedOptionPlaces =
                new ArrayList<>();
        Set<Long> usedHotelIds = new LinkedHashSet<>();
        Set<String> generatedRecommendationKeys = new LinkedHashSet<>();
        int optionNo = 1;

        for (OptionStrategy strategy : OptionStrategy.values()) {
            SequentialSelection selection = createSequentialSelection(
                    routeContexts,
                    strategy,
                    validated.previouslyRecommendedPlaceIds(),
                    generatedOptionPlaces,
                    dailyOverlapLimit
            );
            CourseOptimizeResponse optimized =
                    courseOptimizationService.optimizeForRecommendation(
                            CourseOptimizeRequest.builder()
                                    .transportMode(validated.transportMode())
                                    .placeCandidates(selection.placeCandidates())
                                    .build(),
                            selection.preferredFirstPlaceIdsByDate()
                    );

            if (validated.dailyPlans().size() >= 2) {
                PlaceCandidateDto selectedHotel =
                        selectHotelCandidateForOption(
                                validated.hotelCandidates(),
                                optimized,
                                strategy,
                                validated.previouslyRecommendedPlaceIds(),
                                usedHotelIds
                        );
                if (selectedHotel != null) {
                    optimized =
                            courseOptimizationService
                                    .appendFixedHotelBeforeFinalDayForRecommendation(
                                            optimized,
                                            selectedHotel
                                    );
                    usedHotelIds.add(selectedHotel.getPlaceId());
                } else {
                    log.warn(
                            "다일 추천에 사용할 숙소 후보가 없습니다: resultId={}, strategy={}",
                            request.getResultId(),
                            strategy
                    );
                }
            }

            String recommendationKey =
                    createOptimizedCompositionSignature(
                            optimized.getOptimizedPlaces(),
                            validated.transportMode()
                    );
            if (validated.excludedRecommendationKeys().contains(recommendationKey)
                    || !generatedRecommendationKeys.add(recommendationKey)) {
                log.warn(
                        "후보 부족으로 기존 코스 구성이 다시 선택되었습니다: "
                                + "resultId={}, strategy={}, recommendationKey={}",
                        request.getResultId(),
                        strategy,
                        recommendationKey
                );
            }

            Map<LocalDate, Set<Long>> ordinaryPlacesByDate =
                    ordinaryPlaceIdsByDate(optimized);
            logOverlapResult(
                    request.getResultId(),
                    strategy,
                    ordinaryPlacesByDate,
                    generatedOptionPlaces,
                    dailyOverlapLimit
            );
            generatedOptionPlaces.add(ordinaryPlacesByDate);
            courseOptions.add(toOptionResponse(
                    optionNo++,
                    strategy,
                    optimized,
                    request.getDailyStartTime(),
                    recommendationKey
            ));
        }

        log.info(
                "추천 코스 순차 생성 완료: resultId={}, options={}, days={}, "
                        + "dailyOverlapLimit={}, routeApiCallsDuringGeneration=0",
                request.getResultId(),
                courseOptions.size(),
                validated.dailyPlans().size(),
                dailyOverlapLimit
        );

        return CourseRecommendResponse.builder()
                .resultId(request.getResultId())
                .travelCode(request.getTravelCode())
                .transportMode(validated.transportMode())
                .estimatedTravelTimes(courseOptions.stream()
                        .anyMatch(option -> Boolean.TRUE.equals(
                                option.getEstimatedTravelTimes()
                        )))
                .dailyStartTime(request.getDailyStartTime())
                .optionCount(courseOptions.size())
                .courseOptions(courseOptions)
                .build();
    }

    /** 이전 호출부가 남아 있어도 컴파일되도록 추천 메서드 이름을 한동안 호환한다. */
    @Deprecated
    public CourseRecommendResponse recommendAndSave(
            CourseRecommendRequest request
    ) {
        return recommend(request);
    }

    /** 요청 필수값과 날짜별 선발 수량을 검증하고 모든 후보에 방문 날짜를 적용한다. */
    private ValidatedRecommendation validateAndPrepare(
            CourseRecommendRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("추천 코스 생성 요청은 null일 수 없습니다.");
        }
        if (request.getResultId() == null || request.getResultId() < 1) {
            throw new IllegalArgumentException("설문 결과 ID는 1 이상이어야 합니다.");
        }
        if (request.getTransportMode() == null) {
            throw new IllegalArgumentException("이동수단은 필수입니다.");
        }
        if (request.getDailyStartTime() == null) {
            throw new IllegalArgumentException("일정 시작 시각은 필수입니다.");
        }
        if (request.getDailyPlans() == null
                || request.getDailyPlans().isEmpty()) {
            throw new IllegalArgumentException("날짜별 일정이 한 개 이상 필요합니다.");
        }

        Set<String> excludedRecommendationKeys =
                normalizeExcludedRecommendationKeys(
                        request.getExcludedRecommendationKeys()
                );
        Set<Long> previouslyRecommendedPlaceIds =
                normalizePreviouslyRecommendedPlaceIds(
                        request.getPreviouslyRecommendedPlaceIds()
                );
        Set<Long> combinedPreviousPlaceIds =
                new LinkedHashSet<>(previouslyRecommendedPlaceIds);
        excludedRecommendationKeys.stream()
                .map(this::extractPlaceIds)
                .forEach(combinedPreviousPlaceIds::addAll);

        Map<LocalDate, ValidatedDailyPlan> plansByDate = new TreeMap<>();
        for (DailyPlanRequest dailyPlan : request.getDailyPlans()) {
            ValidatedDailyPlan validatedPlan = validateDailyPlan(dailyPlan);
            if (plansByDate.putIfAbsent(
                    validatedPlan.visitDate(),
                    validatedPlan
            ) != null) {
                throw new IllegalArgumentException(
                        "동일한 방문 날짜를 dailyPlans에 중복해서 보낼 수 없습니다. visitDate="
                                + validatedPlan.visitDate()
                );
            }
        }

        List<ValidatedDailyPlan> dailyPlans =
                new ArrayList<>(plansByDate.values());
        List<PlaceCandidateDto> hotelCandidates = validateHotelCandidates(
                request.getHotelCandidates(),
                dailyPlans.get(0).visitDate()
        );
        applyRerecommendationScores(
                dailyPlans,
                hotelCandidates,
                combinedPreviousPlaceIds
        );

        return new ValidatedRecommendation(
                dailyPlans,
                excludedRecommendationKeys,
                request.getTransportMode(),
                Set.copyOf(combinedPreviousPlaceIds),
                hotelCandidates
        );
    }

    /** 재추천 요청의 제외 키를 검증하고 중복을 제거한다. */
    private Set<String> normalizeExcludedRecommendationKeys(
            List<String> source
    ) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        if (source.size() > MAX_EXCLUDED_RECOMMENDATIONS) {
            throw new IllegalArgumentException(
                    "excludedRecommendationKeys는 최대 "
                            + MAX_EXCLUDED_RECOMMENDATIONS + "개까지 보낼 수 있습니다."
            );
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String key : source) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(
                        "excludedRecommendationKeys에는 빈 값을 넣을 수 없습니다."
                );
            }
            normalized.add(key.trim());
        }
        return Set.copyOf(normalized);
    }

    /** 프런트가 전달한 이전 장소 ID를 양의 정수·중복 제거 형태로 검증한다. */
    private Set<Long> normalizePreviouslyRecommendedPlaceIds(
            List<Long> source
    ) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        if (source.size() > MAX_PREVIOUSLY_RECOMMENDED_PLACES) {
            throw new IllegalArgumentException(
                    "previouslyRecommendedPlaceIds는 최대 "
                            + MAX_PREVIOUSLY_RECOMMENDED_PLACES
                            + "개까지 보낼 수 있습니다."
            );
        }

        Set<Long> normalized = new LinkedHashSet<>();
        for (Long placeId : source) {
            if (placeId == null || placeId < 1) {
                throw new IllegalArgumentException(
                        "previouslyRecommendedPlaceIds에는 1 이상의 장소 ID만 넣을 수 있습니다."
                );
            }
            normalized.add(placeId);
        }
        return Set.copyOf(normalized);
    }

    /** 일반 방문 후보와 섞지 않을 HOTEL 후보를 별도로 검증하고 복사한다. */
    private List<PlaceCandidateDto> validateHotelCandidates(
            List<PlaceCandidateDto> source,
            LocalDate referenceDate
    ) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }

        List<PlaceCandidateDto> hotels = new ArrayList<>();
        Set<Long> hotelIds = new HashSet<>();
        for (PlaceCandidateDto candidate : source) {
            PlaceCandidateDto copied = copyCandidateWithDate(
                    candidate,
                    referenceDate,
                    false
            );
            if (!"HOTEL".equals(copied.getCategory())) {
                throw new IllegalArgumentException(
                        "hotelCandidates에는 HOTEL 카테고리만 넣을 수 있습니다."
                );
            }
            validateCandidateForRecommendation(copied);
            if (!hotelIds.add(copied.getPlaceId())) {
                throw new IllegalArgumentException(
                        "hotelCandidates에 장소 ID가 중복되었습니다. placeId="
                                + copied.getPlaceId()
                );
            }
            hotels.add(copied);
        }
        return hotels;
    }

    private void validateCandidateForRecommendation(
            PlaceCandidateDto candidate
    ) {
        if (candidate.getPlaceName() == null
                || candidate.getPlaceName().isBlank()) {
            throw new IllegalArgumentException("장소명은 필수입니다.");
        }
        if (candidate.getRecommendationScore() == null
                || !Double.isFinite(candidate.getRecommendationScore())) {
            throw new IllegalArgumentException("추천 점수는 유한한 숫자여야 합니다.");
        }
        if (candidate.getLatitude() == null
                || candidate.getLongitude() == null) {
            throw new IllegalArgumentException("장소의 위도와 경도는 필수입니다.");
        }
        distanceService.calculateDistanceKm(
                candidate.getLatitude(),
                candidate.getLongitude(),
                candidate.getLatitude(),
                candidate.getLongitude()
        );
    }

    /**
     * 재추천일 때 이전 장소는 20점 감점하고 새 장소는 5점 가산한 뒤,
     * 화면 점수만 다시 70~95 범위로 보정한다.
     */
    private void applyRerecommendationScores(
            List<ValidatedDailyPlan> dailyPlans,
            List<PlaceCandidateDto> hotelCandidates,
            Set<Long> previouslyRecommendedPlaceIds
    ) {
        if (previouslyRecommendedPlaceIds.isEmpty()) {
            return;
        }

        List<PlaceCandidateDto> candidates = new ArrayList<>();
        for (ValidatedDailyPlan dailyPlan : dailyPlans) {
            for (PlaceCandidateDto candidate : dailyPlan.placeCandidates()) {
                collectCandidateAndAlternatives(candidate, candidates);
            }
        }
        for (PlaceCandidateDto hotelCandidate : hotelCandidates) {
            collectCandidateAndAlternatives(hotelCandidate, candidates);
        }
        if (candidates.isEmpty()) {
            return;
        }

        double minimum = candidates.stream()
                .mapToDouble(candidate -> adjustedRerecommendationScore(
                        candidate,
                        previouslyRecommendedPlaceIds
                ))
                .min()
                .orElse(0.0);
        double maximum = candidates.stream()
                .mapToDouble(candidate -> adjustedRerecommendationScore(
                        candidate,
                        previouslyRecommendedPlaceIds
                ))
                .max()
                .orElse(0.0);

        for (PlaceCandidateDto candidate : candidates) {
            double adjusted = adjustedRerecommendationScore(
                    candidate,
                    previouslyRecommendedPlaceIds
            );
            candidate.setRecommendationScore(normalizeDisplayScore(
                    adjusted,
                    minimum,
                    maximum
            ));
        }
    }

    private void collectCandidateAndAlternatives(
            PlaceCandidateDto candidate,
            List<PlaceCandidateDto> output
    ) {
        output.add(candidate);
        if (candidate.getAlternativeCandidates() != null) {
            output.addAll(candidate.getAlternativeCandidates());
        }
    }

    private double adjustedRerecommendationScore(
            PlaceCandidateDto candidate,
            Set<Long> previouslyRecommendedPlaceIds
    ) {
        double score = valueOrZero(candidate.getRecommendationScore());
        return previouslyRecommendedPlaceIds.contains(candidate.getPlaceId())
                ? score - PREVIOUSLY_RECOMMENDED_PENALTY
                : score + NEW_PLACE_BONUS;
    }

    private double normalizeDisplayScore(
            double value,
            double minimum,
            double maximum
    ) {
        if (Math.abs(maximum - minimum) < EPSILON) {
            return EQUAL_SCORE_DISPLAY_VALUE;
        }
        double normalized = DISPLAY_SCORE_MIN
                + (value - minimum)
                / (maximum - minimum)
                * (DISPLAY_SCORE_MAX - DISPLAY_SCORE_MIN);
        return round(
                Math.max(
                        DISPLAY_SCORE_MIN,
                        Math.min(DISPLAY_SCORE_MAX, normalized)
                ),
                1
        );
    }

    /** 날짜 한 건의 목표 수량과 후보 풀을 검증한다. */
    private ValidatedDailyPlan validateDailyPlan(DailyPlanRequest dailyPlan) {
        if (dailyPlan == null) {
            throw new IllegalArgumentException("날짜별 일정은 null일 수 없습니다.");
        }
        LocalDate visitDate = dailyPlan.getVisitDate();
        if (visitDate == null) {
            throw new IllegalArgumentException("날짜별 일정의 방문 날짜는 필수입니다.");
        }
        Integer targetPlaceCount = dailyPlan.getTargetPlaceCount();
        if (targetPlaceCount == null || targetPlaceCount < 1) {
            throw new IllegalArgumentException(
                    "targetPlaceCount는 1 이상이어야 합니다. visitDate=" + visitDate
            );
        }
        if (dailyPlan.getCategoryTargets() == null
                || dailyPlan.getCategoryTargets().isEmpty()) {
            throw new IllegalArgumentException(
                    "categoryTargets는 필수입니다. visitDate=" + visitDate
            );
        }
        if (dailyPlan.getPlaceCandidates() == null
                || dailyPlan.getPlaceCandidates().isEmpty()) {
            throw new IllegalArgumentException(
                    "각 날짜에는 장소 후보가 한 개 이상 필요합니다. visitDate="
                            + visitDate
            );
        }

        Map<String, Integer> categoryTargets = normalizeCategoryTargets(
                dailyPlan.getCategoryTargets(),
                visitDate
        );
        int categoryTargetSum = categoryTargets.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        if (categoryTargetSum < targetPlaceCount) {
            throw new IllegalArgumentException(
                    "categoryTargets 합계는 targetPlaceCount 이상이어야 합니다. visitDate="
                            + visitDate
            );
        }

        List<PlaceCandidateDto> candidates = new ArrayList<>();
        Set<Long> candidateIds = new HashSet<>();
        for (PlaceCandidateDto candidate : dailyPlan.getPlaceCandidates()) {
            PlaceCandidateDto copied = copyCandidateWithDate(
                    candidate,
                    visitDate,
                    true
            );
            validateCandidateForRecommendation(copied);
            if (!candidateIds.add(copied.getPlaceId())) {
                throw new IllegalArgumentException(
                        "같은 날짜의 placeCandidates에 장소 ID가 중복되었습니다. placeId="
                                + copied.getPlaceId()
                );
            }
            candidates.add(copied);
        }

        if (candidates.size() < targetPlaceCount) {
            throw new IllegalArgumentException(
                    "장소 후보 수가 targetPlaceCount보다 적습니다. visitDate="
                            + visitDate
            );
        }

        for (String category : SUPPORTED_CATEGORIES) {
            int required = categoryTargets.getOrDefault(category, 0);
            long available = candidates.stream()
                    .filter(candidate -> category.equals(candidate.getCategory()))
                    .count();
            if (available < required) {
                throw new IllegalArgumentException(
                        "카테고리 후보가 목표 개수보다 적습니다. visitDate="
                                + visitDate
                                + ", category=" + category
                                + ", required=" + required
                                + ", available=" + available
                );
            }
        }

        Map<String, Integer> finalCategoryTargets =
                deriveFinalCategoryTargets(
                        categoryTargets,
                        targetPlaceCount
                );

        return new ValidatedDailyPlan(
                visitDate,
                targetPlaceCount,
                finalCategoryTargets,
                candidates
        );
    }

    /**
     * 후보 풀 카테고리 수를 비율로 축소해 실제 코스에 뽑을 정확한 개수로 변환한다.
     *
     * <p>P형 48개가 TOUR 24·RESTAURANT 16·CAFE 8로 전달되면 최종 6곳을
     * TOUR 3·RESTAURANT 2·CAFE 1로 선발한다.</p>
     */
    private Map<String, Integer> deriveFinalCategoryTargets(
            Map<String, Integer> candidateCategoryTargets,
            int targetPlaceCount
    ) {
        int candidateTargetSum = candidateCategoryTargets.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        Map<String, Integer> finalTargets = new LinkedHashMap<>();
        Map<String, Double> remainders = new LinkedHashMap<>();
        int assigned = 0;

        for (String category : SUPPORTED_CATEGORIES) {
            int candidateCount =
                    candidateCategoryTargets.getOrDefault(category, 0);
            double proportionalCount = (double) candidateCount
                    * targetPlaceCount
                    / candidateTargetSum;
            int baseCount = (int) Math.floor(proportionalCount);
            finalTargets.put(category, baseCount);
            remainders.put(category, proportionalCount - baseCount);
            assigned += baseCount;
        }

        while (assigned < targetPlaceCount) {
            String selectedCategory = SUPPORTED_CATEGORIES.stream()
                    .filter(category -> finalTargets.get(category)
                            < candidateCategoryTargets.get(category))
                    .max(Comparator
                            .comparingDouble(
                                    (String category) -> remainders.get(category)
                            )
                            .thenComparingInt(
                                    category -> -SUPPORTED_CATEGORIES.indexOf(category)
                            ))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "카테고리 후보 비율로 최종 장소 수를 배분할 수 없습니다."
                    ));
            finalTargets.put(
                    selectedCategory,
                    finalTargets.get(selectedCategory) + 1
            );
            remainders.put(selectedCategory, -1.0);
            assigned++;
        }
        return finalTargets;
    }

    /** categoryTargets 키를 대문자 기본 카테고리로 통일하고 누락 카테고리는 0으로 채운다. */
    private Map<String, Integer> normalizeCategoryTargets(
            Map<String, Integer> source,
            LocalDate visitDate
    ) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (String category : SUPPORTED_CATEGORIES) {
            normalized.put(category, 0);
        }

        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String category = normalizeCategory(entry.getKey());
            Integer count = entry.getValue();
            if (count == null || count < 0) {
                throw new IllegalArgumentException(
                        "categoryTargets 값은 0 이상이어야 합니다. visitDate="
                                + visitDate + ", category=" + category
                );
            }
            normalized.put(category, count);
        }
        return normalized;
    }

    /** 날짜마다 한 번만 외부 호출 없는 후보 풀 추정 행렬을 생성한다. */
    private Map<LocalDate, DailyRouteContext> createDailyRouteContexts(
            List<ValidatedDailyPlan> dailyPlans,
            TransportMode transportMode
    ) {
        Map<LocalDate, DailyRouteContext> contexts = new TreeMap<>();
        for (ValidatedDailyPlan dailyPlan : dailyPlans) {
            RouteMatrix routeMatrix =
                    distanceService.calculateCandidatePoolMatrix(
                            dailyPlan.placeCandidates(),
                            transportMode
                    );
            contexts.put(
                    dailyPlan.visitDate(),
                    new DailyRouteContext(
                            dailyPlan,
                            routeMatrix,
                            createCandidateIndexes(
                                    dailyPlan.placeCandidates()
                            ),
                            ScoreRange.from(dailyPlan.placeCandidates())
                    )
            );
        }
        return contexts;
    }

    /** travelCode 마지막 글자를 우선하고 없으면 하루 목표 장소 수로 P/R 중복 기준을 정한다. */
    private int resolveDailyOverlapLimit(
            String travelCode,
            List<ValidatedDailyPlan> dailyPlans
    ) {
        if (travelCode != null && !travelCode.isBlank()) {
            char scheduleType = Character.toUpperCase(
                    travelCode.trim().charAt(travelCode.trim().length() - 1)
            );
            if (scheduleType == 'P') {
                return PACKED_DAILY_OVERLAP_LIMIT;
            }
            if (scheduleType == 'R') {
                return RELAXED_DAILY_OVERLAP_LIMIT;
            }
        }
        boolean packed = dailyPlans.stream()
                .anyMatch(plan -> plan.targetPlaceCount() >= 6);
        return packed
                ? PACKED_DAILY_OVERLAP_LIMIT
                : RELAXED_DAILY_OVERLAP_LIMIT;
    }

    /** 한 전략의 모든 날짜를 선발하고 다음 전략에서 사용할 장소 ID를 반환한다. */
    private SequentialSelection createSequentialSelection(
            Map<LocalDate, DailyRouteContext> routeContexts,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        List<PlaceCandidateDto> selectedCandidates = new ArrayList<>();
        Map<LocalDate, Long> preferredFirstPlaceIds = new TreeMap<>();

        for (Map.Entry<LocalDate, DailyRouteContext> entry
                : routeContexts.entrySet()) {
            DailyPick dailyPick = selectDailyPlaces(
                    entry.getValue(),
                    strategy,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit
            );
            Set<Long> selectedIds = dailyPick.placeCandidates().stream()
                    .map(PlaceCandidateDto::getPlaceId)
                    .collect(java.util.stream.Collectors.toSet());
            Set<Long> blockedAlternativeIds =
                    createBlockedAlternativeIds(
                            entry.getKey(),
                            previouslyRecommendedPlaceIds,
                            generatedOptionPlaces
                    );

            for (PlaceCandidateDto candidate
                    : dailyPick.placeCandidates()) {
                selectedCandidates.add(copyCandidateForSelection(
                        candidate,
                        selectedIds,
                        blockedAlternativeIds
                ));
            }
            preferredFirstPlaceIds.put(
                    entry.getKey(),
                    dailyPick.firstPlaceId()
            );
        }

        return new SequentialSelection(
                List.copyOf(selectedCandidates),
                Map.copyOf(preferredFirstPlaceIds)
        );
    }

    /**
     * 모든 조합 대신 가능한 출발 후보만 순회하고, 각 출발 후보에서 필요한 장소를 탐욕적으로
     * 채운다. 최대 후보가 48개여도 하루 약 48개의 압축 결과만 비교한다.
     */
    private DailyPick selectDailyPlaces(
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        List<PlaceCandidateDto> possibleFirstPlaces =
                context.plan().placeCandidates().stream()
                        .filter(candidate -> context.plan()
                                .categoryTargets()
                                .getOrDefault(candidate.getCategory(), 0) > 0)
                        .toList();
        if (possibleFirstPlaces.isEmpty()) {
            throw new IllegalArgumentException(
                    "선발 가능한 장소 후보가 없습니다. visitDate="
                            + context.plan().visitDate()
            );
        }

        DailyPick best = null;
        for (PlaceCandidateDto firstCandidate : possibleFirstPlaces) {
            DailyPick candidatePick = buildGreedyDailyPick(
                    context,
                    strategy,
                    firstCandidate,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit
            );
            if (best == null
                    || compareDailyPicks(
                            candidatePick,
                            best,
                            strategy,
                            context.scoreRange()
                    ) < 0) {
                best = candidatePick;
            }
        }

        if (best == null
                || best.placeCandidates().size()
                != context.plan().targetPlaceCount()) {
            throw new IllegalArgumentException(
                    "카테고리 목표를 만족하는 순차 코스를 만들 수 없습니다. visitDate="
                            + context.plan().visitDate()
            );
        }
        return best;
    }

    /** 한 출발 후보에서 남은 카테고리 수량을 가장 좋은 다음 장소로 하나씩 채운다. */
    private DailyPick buildGreedyDailyPick(
            DailyRouteContext context,
            OptionStrategy strategy,
            PlaceCandidateDto firstCandidate,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        Map<String, Integer> remainingTargets =
                new LinkedHashMap<>(context.plan().categoryTargets());
        List<PlaceCandidateDto> selected = new ArrayList<>();
        Set<Long> selectedIds = new LinkedHashSet<>();

        addSelectedCandidate(
                firstCandidate,
                selected,
                selectedIds,
                remainingTargets
        );

        while (selected.size() < context.plan().targetPlaceCount()) {
            List<PlaceCandidateDto> available =
                    context.plan().placeCandidates().stream()
                            .filter(candidate -> !selectedIds.contains(
                                    candidate.getPlaceId()
                            ))
                            .filter(candidate -> remainingTargets.getOrDefault(
                                    candidate.getCategory(),
                                    0
                            ) > 0)
                            .toList();
            if (available.isEmpty()) {
                throw new IllegalArgumentException(
                        "카테고리별 장소 후보가 부족합니다. visitDate="
                                + context.plan().visitDate()
                );
            }

            PlaceCandidateDto next = available.stream()
                    .min((left, right) -> compareNextCandidates(
                            left,
                            right,
                            selected,
                            context,
                            strategy,
                            previouslyRecommendedPlaceIds,
                            generatedOptionPlaces,
                            dailyOverlapLimit
                    ))
                    .orElseThrow();
            addSelectedCandidate(
                    next,
                    selected,
                    selectedIds,
                    remainingTargets
            );
        }

        Long firstPlaceId = strategy == OptionStrategy.PREFERENCE
                ? selected.stream()
                        .sorted(Comparator
                                .comparingDouble(
                                        (PlaceCandidateDto candidate)
                                                -> valueOrZero(
                                                candidate.getRecommendationScore()
                                        )
                                )
                                .reversed()
                                .thenComparing(PlaceCandidateDto::getPlaceId))
                        .map(PlaceCandidateDto::getPlaceId)
                        .findFirst()
                        .orElseThrow()
                : firstCandidate.getPlaceId();
        RouteEstimate routeEstimate = estimateRoute(
                selected,
                firstPlaceId,
                context
        );
        PickReuseMetrics reuseMetrics = calculatePickReuseMetrics(
                selected,
                context.plan().visitDate(),
                previouslyRecommendedPlaceIds,
                generatedOptionPlaces,
                dailyOverlapLimit
        );
        double recommendationScore = selected.stream()
                .mapToDouble(candidate -> valueOrZero(
                        candidate.getRecommendationScore()
                ))
                .sum();

        return new DailyPick(
                List.copyOf(selected),
                firstPlaceId,
                recommendationScore,
                routeEstimate.travelTimeMinutes(),
                routeEstimate.distanceKm(),
                reuseMetrics.previousRecommendationCount(),
                reuseMetrics.overlapExcess(),
                reuseMetrics.totalOverlap(),
                selected.stream()
                        .map(PlaceCandidateDto::getPlaceId)
                        .sorted()
                        .map(String::valueOf)
                        .reduce((left, right) -> left + "," + right)
                        .orElse("")
        );
    }

    private void addSelectedCandidate(
            PlaceCandidateDto candidate,
            List<PlaceCandidateDto> selected,
            Set<Long> selectedIds,
            Map<String, Integer> remainingTargets
    ) {
        String category = candidate.getCategory();
        int remaining = remainingTargets.getOrDefault(category, 0);
        if (remaining < 1 || !selectedIds.add(candidate.getPlaceId())) {
            throw new IllegalArgumentException(
                    "순차 코스 선발 중 중복되거나 불필요한 장소가 선택되었습니다. placeId="
                            + candidate.getPlaceId()
            );
        }
        selected.add(candidate);
        remainingTargets.put(category, remaining - 1);
    }

    /**
     * 재추천 장소 완전 제외와 옵션 간 중복 상한을 전략 점수보다 먼저 비교한다.
     * 둘 중 하나를 어겨야만 카테고리 수량을 채울 수 있을 때만 다음 단계 후보를 사용한다.
     */
    private int compareNextCandidates(
            PlaceCandidateDto left,
            PlaceCandidateDto right,
            List<PlaceCandidateDto> selected,
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        int leftRestriction = candidateRestrictionLevel(
                left,
                selected,
                context.plan().visitDate(),
                previouslyRecommendedPlaceIds,
                generatedOptionPlaces,
                dailyOverlapLimit
        );
        int rightRestriction = candidateRestrictionLevel(
                right,
                selected,
                context.plan().visitDate(),
                previouslyRecommendedPlaceIds,
                generatedOptionPlaces,
                dailyOverlapLimit
        );
        int comparison = Integer.compare(leftRestriction, rightRestriction);
        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(
                priorOptionUseCount(
                        left.getPlaceId(),
                        context.plan().visitDate(),
                        generatedOptionPlaces
                ),
                priorOptionUseCount(
                        right.getPlaceId(),
                        context.plan().visitDate(),
                        generatedOptionPlaces
                )
        );
        if (comparison != 0) {
            return comparison;
        }

        if (strategy == OptionStrategy.PREFERENCE) {
            comparison = Double.compare(
                    valueOrZero(right.getRecommendationScore()),
                    valueOrZero(left.getRecommendationScore())
            );
        } else {
            PlaceCandidateDto current = selected.get(selected.size() - 1);
            int currentIndex = context.candidateIndexes()
                    .get(current.getPlaceId());
            int leftIndex = context.candidateIndexes()
                    .get(left.getPlaceId());
            int rightIndex = context.candidateIndexes()
                    .get(right.getPlaceId());

            if (strategy == OptionStrategy.MIN_DISTANCE) {
                comparison = Double.compare(
                        context.routeMatrix().getTravelTimeMinutes(
                                currentIndex,
                                leftIndex
                        ),
                        context.routeMatrix().getTravelTimeMinutes(
                                currentIndex,
                                rightIndex
                        )
                );
                if (comparison == 0) {
                    comparison = Double.compare(
                            context.routeMatrix().getDistanceKm(
                                    currentIndex,
                                    leftIndex
                            ),
                            context.routeMatrix().getDistanceKm(
                                    currentIndex,
                                    rightIndex
                            )
                    );
                }
            } else {
                comparison = Double.compare(
                        candidateBalancedUtility(
                                right,
                                currentIndex,
                                rightIndex,
                                context
                        ),
                        candidateBalancedUtility(
                                left,
                                currentIndex,
                                leftIndex,
                                context
                        )
                );
            }
        }

        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(
                valueOrZero(right.getRecommendationScore()),
                valueOrZero(left.getRecommendationScore())
        );
        return comparison != 0
                ? comparison
                : left.getPlaceId().compareTo(right.getPlaceId());
    }

    /**
     * 제한 단계 0은 완전 신규·중복 상한 이내, 1은 현재 옵션 간 상한만 완화,
     * 2는 재추천 장소만 재사용, 3은 두 제한을 모두 완화한 후보이다.
     */
    private int candidateRestrictionLevel(
            PlaceCandidateDto candidate,
            List<PlaceCandidateDto> selected,
            LocalDate visitDate,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        boolean previouslyRecommended =
                previouslyRecommendedPlaceIds.contains(
                        candidate.getPlaceId()
                );
        boolean exceedsDailyOverlap = wouldExceedDailyOverlap(
                candidate.getPlaceId(),
                selected,
                visitDate,
                generatedOptionPlaces,
                dailyOverlapLimit
        );
        if (!previouslyRecommended && !exceedsDailyOverlap) {
            return 0;
        }
        if (!previouslyRecommended) {
            return 1;
        }
        return exceedsDailyOverlap ? 3 : 2;
    }

    private boolean wouldExceedDailyOverlap(
            Long candidatePlaceId,
            List<PlaceCandidateDto> selected,
            LocalDate visitDate,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        Set<Long> selectedIds = selected.stream()
                .map(PlaceCandidateDto::getPlaceId)
                .collect(java.util.stream.Collectors.toSet());
        for (Map<LocalDate, Set<Long>> optionPlaces
                : generatedOptionPlaces) {
            Set<Long> usedOnDate = optionPlaces.getOrDefault(
                    visitDate,
                    Set.of()
            );
            long overlap = selectedIds.stream()
                    .filter(usedOnDate::contains)
                    .count();
            if (usedOnDate.contains(candidatePlaceId)) {
                overlap++;
            }
            if (overlap > dailyOverlapLimit) {
                return true;
            }
        }
        return false;
    }

    private int priorOptionUseCount(
            Long placeId,
            LocalDate visitDate,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces
    ) {
        return (int) generatedOptionPlaces.stream()
                .filter(option -> option.getOrDefault(
                        visitDate,
                        Set.of()
                ).contains(placeId))
                .count();
    }

    private double candidateBalancedUtility(
            PlaceCandidateDto candidate,
            int currentIndex,
            int candidateIndex,
            DailyRouteContext context
    ) {
        double preference = normalizeHigher(
                valueOrZero(candidate.getRecommendationScore()),
                context.scoreRange().minimum(),
                context.scoreRange().maximum()
        );
        double travelEfficiency = inverseCost(
                context.routeMatrix().getTravelTimeMinutes(
                        currentIndex,
                        candidateIndex
                )
        );
        double distanceEfficiency = inverseCost(
                context.routeMatrix().getDistanceKm(
                        currentIndex,
                        candidateIndex
                )
        );
        return preference * 0.50
                + travelEfficiency * 0.30
                + distanceEfficiency * 0.20;
    }

    /** 제한 위반 수를 먼저 최소화한 뒤 해당 전략의 실제 품질을 비교한다. */
    private int compareDailyPicks(
            DailyPick left,
            DailyPick right,
            OptionStrategy strategy,
            ScoreRange scoreRange
    ) {
        int comparison = Integer.compare(
                left.previousRecommendationCount(),
                right.previousRecommendationCount()
        );
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                left.overlapExcess(),
                right.overlapExcess()
        );
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                left.totalOverlap(),
                right.totalOverlap()
        );
        if (comparison != 0) {
            return comparison;
        }

        if (strategy == OptionStrategy.PREFERENCE) {
            comparison = Double.compare(
                    right.recommendationScore(),
                    left.recommendationScore()
            );
            if (comparison == 0) {
                comparison = Double.compare(
                        left.travelTimeMinutes(),
                        right.travelTimeMinutes()
                );
            }
        } else if (strategy == OptionStrategy.MIN_DISTANCE) {
            comparison = Double.compare(
                    left.travelTimeMinutes(),
                    right.travelTimeMinutes()
            );
            if (comparison == 0) {
                comparison = Double.compare(
                        left.distanceKm(),
                        right.distanceKm()
                );
            }
        } else {
            comparison = Double.compare(
                    dailyBalancedUtility(right, scoreRange),
                    dailyBalancedUtility(left, scoreRange)
            );
        }

        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(
                right.recommendationScore(),
                left.recommendationScore()
        );
        return comparison != 0
                ? comparison
                : left.signature().compareTo(right.signature());
    }

    private double dailyBalancedUtility(
            DailyPick pick,
            ScoreRange scoreRange
    ) {
        double averageScore = pick.placeCandidates().isEmpty()
                ? 0.0
                : pick.recommendationScore()
                / pick.placeCandidates().size();
        double preference = normalizeHigher(
                averageScore,
                scoreRange.minimum(),
                scoreRange.maximum()
        );
        return preference * 0.50
                + inverseCost(pick.travelTimeMinutes()) * 0.30
                + inverseCost(pick.distanceKm()) * 0.20;
    }

    private PickReuseMetrics calculatePickReuseMetrics(
            List<PlaceCandidateDto> selected,
            LocalDate visitDate,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        Set<Long> selectedIds = selected.stream()
                .map(PlaceCandidateDto::getPlaceId)
                .collect(java.util.stream.Collectors.toSet());
        int previousCount = (int) selectedIds.stream()
                .filter(previouslyRecommendedPlaceIds::contains)
                .count();
        int overlapExcess = 0;
        int totalOverlap = 0;

        for (Map<LocalDate, Set<Long>> optionPlaces
                : generatedOptionPlaces) {
            Set<Long> usedOnDate = optionPlaces.getOrDefault(
                    visitDate,
                    Set.of()
            );
            int overlap = (int) selectedIds.stream()
                    .filter(usedOnDate::contains)
                    .count();
            totalOverlap += overlap;
            overlapExcess += Math.max(0, overlap - dailyOverlapLimit);
        }
        return new PickReuseMetrics(
                previousCount,
                overlapExcess,
                totalOverlap
        );
    }

    /** 선택한 출발 장소를 고정하고 최근접 이웃 순서의 거리·시간을 추정한다. */
    private RouteEstimate estimateRoute(
            List<PlaceCandidateDto> candidates,
            Long firstPlaceId,
            DailyRouteContext context
    ) {
        if (candidates.size() < 2) {
            return new RouteEstimate(0.0, 0.0);
        }

        List<PlaceCandidateDto> remaining = new ArrayList<>(candidates);
        PlaceCandidateDto current = remaining.stream()
                .filter(candidate -> candidate.getPlaceId().equals(
                        firstPlaceId
                ))
                .findFirst()
                .orElseThrow();
        remaining.remove(current);

        double totalDistanceKm = 0.0;
        double totalTravelTimeMinutes = 0.0;
        while (!remaining.isEmpty()) {
            PlaceCandidateDto previous = current;
            int previousIndex = context.candidateIndexes()
                    .get(previous.getPlaceId());
            current = remaining.stream()
                    .min(Comparator
                            .comparingDouble(
                                    (PlaceCandidateDto candidate)
                                            -> context.routeMatrix()
                                            .getTravelTimeMinutes(
                                                    previousIndex,
                                                    context.candidateIndexes()
                                                            .get(candidate.getPlaceId())
                                            )
                            )
                            .thenComparingDouble(
                                    candidate -> context.routeMatrix()
                                            .getDistanceKm(
                                                    previousIndex,
                                                    context.candidateIndexes()
                                                            .get(candidate.getPlaceId())
                                            )
                            )
                            .thenComparing(PlaceCandidateDto::getPlaceId))
                    .orElseThrow();
            int currentIndex = context.candidateIndexes()
                    .get(current.getPlaceId());
            totalDistanceKm += context.routeMatrix().getDistanceKm(
                    previousIndex,
                    currentIndex
            );
            totalTravelTimeMinutes += context.routeMatrix()
                    .getTravelTimeMinutes(
                            previousIndex,
                            currentIndex
                    );
            remaining.remove(current);
        }
        return new RouteEstimate(
                totalDistanceKm,
                totalTravelTimeMinutes
        );
    }

    /** 앞 코스·재추천에 이미 나온 장소는 대체 후보에서도 다시 들어오지 않게 한다. */
    private Set<Long> createBlockedAlternativeIds(
            LocalDate visitDate,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces
    ) {
        Set<Long> blocked = new LinkedHashSet<>(
                previouslyRecommendedPlaceIds
        );
        for (Map<LocalDate, Set<Long>> optionPlaces
                : generatedOptionPlaces) {
            blocked.addAll(optionPlaces.getOrDefault(
                    visitDate,
                    Set.of()
            ));
        }
        return blocked;
    }

    private PlaceCandidateDto copyCandidateForSelection(
            PlaceCandidateDto source,
            Set<Long> selectedIds,
            Set<Long> blockedAlternativeIds
    ) {
        PlaceCandidateDto copied = copyCandidateWithDate(
                source,
                source.getVisitDate(),
                false
        );
        List<PlaceCandidateDto> alternatives =
                source.getAlternativeCandidates() == null
                        ? List.of()
                        : source.getAlternativeCandidates().stream()
                        .filter(alternative -> source.getCategory().equals(
                                alternative.getCategory()
                        ))
                        .filter(alternative -> !selectedIds.contains(
                                alternative.getPlaceId()
                        ))
                        .filter(alternative -> !blockedAlternativeIds.contains(
                                alternative.getPlaceId()
                        ))
                        .map(alternative -> copyCandidateWithDate(
                                alternative,
                                source.getVisitDate(),
                                false
                        ))
                        .toList();
        copied.setAlternativeCandidates(new ArrayList<>(alternatives));
        return copied;
    }

    /** 코스별 마지막 일반 장소와 가까운 숙소를 전략에 맞춰 독립적으로 선택한다. */
    private PlaceCandidateDto selectHotelCandidateForOption(
            List<PlaceCandidateDto> hotelCandidates,
            CourseOptimizeResponse optimized,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            Set<Long> usedHotelIds
    ) {
        if (hotelCandidates == null || hotelCandidates.isEmpty()) {
            return null;
        }

        List<OptimizedPlaceDto> lastPlaces =
                lastOrdinaryPlacesBeforeFinalDay(optimized);
        List<HotelEvaluation> evaluations = hotelCandidates.stream()
                .map(hotel -> new HotelEvaluation(
                        hotel,
                        averageDirectDistanceKm(lastPlaces, hotel)
                ))
                .toList();
        ScoreRange hotelScoreRange = ScoreRange.from(hotelCandidates);
        double minimumDistance = evaluations.stream()
                .mapToDouble(HotelEvaluation::averageDistanceKm)
                .min()
                .orElse(0.0);
        double maximumDistance = evaluations.stream()
                .mapToDouble(HotelEvaluation::averageDistanceKm)
                .max()
                .orElse(0.0);

        return evaluations.stream()
                .sorted((left, right) -> compareHotels(
                        left,
                        right,
                        strategy,
                        previouslyRecommendedPlaceIds,
                        usedHotelIds,
                        hotelScoreRange,
                        minimumDistance,
                        maximumDistance
                ))
                .map(HotelEvaluation::hotel)
                .findFirst()
                .orElse(null);
    }

    private int compareHotels(
            HotelEvaluation left,
            HotelEvaluation right,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            Set<Long> usedHotelIds,
            ScoreRange scoreRange,
            double minimumDistance,
            double maximumDistance
    ) {
        int comparison = Integer.compare(
                hotelReuseLevel(
                        left.hotel().getPlaceId(),
                        previouslyRecommendedPlaceIds,
                        usedHotelIds
                ),
                hotelReuseLevel(
                        right.hotel().getPlaceId(),
                        previouslyRecommendedPlaceIds,
                        usedHotelIds
                )
        );
        if (comparison != 0) {
            return comparison;
        }

        if (strategy == OptionStrategy.PREFERENCE) {
            comparison = Double.compare(
                    valueOrZero(right.hotel().getRecommendationScore()),
                    valueOrZero(left.hotel().getRecommendationScore())
            );
            if (comparison == 0) {
                comparison = Double.compare(
                        left.averageDistanceKm(),
                        right.averageDistanceKm()
                );
            }
        } else if (strategy == OptionStrategy.MIN_DISTANCE) {
            comparison = Double.compare(
                    left.averageDistanceKm(),
                    right.averageDistanceKm()
            );
            if (comparison == 0) {
                comparison = Double.compare(
                        valueOrZero(right.hotel().getRecommendationScore()),
                        valueOrZero(left.hotel().getRecommendationScore())
                );
            }
        } else {
            comparison = Double.compare(
                    hotelBalancedUtility(
                            right,
                            scoreRange,
                            minimumDistance,
                            maximumDistance
                    ),
                    hotelBalancedUtility(
                            left,
                            scoreRange,
                            minimumDistance,
                            maximumDistance
                    )
            );
        }
        return comparison != 0
                ? comparison
                : left.hotel().getPlaceId()
                .compareTo(right.hotel().getPlaceId());
    }

    private int hotelReuseLevel(
            Long hotelId,
            Set<Long> previouslyRecommendedPlaceIds,
            Set<Long> usedHotelIds
    ) {
        boolean previous =
                previouslyRecommendedPlaceIds.contains(hotelId);
        boolean usedCurrent = usedHotelIds.contains(hotelId);
        if (!previous && !usedCurrent) {
            return 0;
        }
        if (!previous) {
            return 1;
        }
        return usedCurrent ? 3 : 2;
    }

    private double hotelBalancedUtility(
            HotelEvaluation evaluation,
            ScoreRange scoreRange,
            double minimumDistance,
            double maximumDistance
    ) {
        double preference = normalizeHigher(
                valueOrZero(
                        evaluation.hotel().getRecommendationScore()
                ),
                scoreRange.minimum(),
                scoreRange.maximum()
        );
        double distanceEfficiency = normalizeLower(
                evaluation.averageDistanceKm(),
                minimumDistance,
                maximumDistance
        );
        return preference * 0.50 + distanceEfficiency * 0.50;
    }

    private List<OptimizedPlaceDto> lastOrdinaryPlacesBeforeFinalDay(
            CourseOptimizeResponse optimized
    ) {
        if (optimized == null
                || optimized.getOptimizedPlaces() == null
                || optimized.getOptimizedPlaces().isEmpty()) {
            return List.of();
        }
        LocalDate finalDate = optimized.getOptimizedPlaces().stream()
                .map(OptimizedPlaceDto::getVisitDate)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        Map<LocalDate, OptimizedPlaceDto> lastByDate = new TreeMap<>();
        for (OptimizedPlaceDto place : optimized.getOptimizedPlaces()) {
            if (place.getVisitDate().equals(finalDate)
                    || "HOTEL".equalsIgnoreCase(place.getCategory())) {
                continue;
            }
            OptimizedPlaceDto current = lastByDate.get(
                    place.getVisitDate()
            );
            if (current == null
                    || valueOrZero(place.getVisitOrder())
                    > valueOrZero(current.getVisitOrder())) {
                lastByDate.put(place.getVisitDate(), place);
            }
        }
        return List.copyOf(lastByDate.values());
    }

    private double averageDirectDistanceKm(
            List<OptimizedPlaceDto> lastPlaces,
            PlaceCandidateDto hotel
    ) {
        if (lastPlaces.isEmpty()) {
            return 0.0;
        }
        return lastPlaces.stream()
                .mapToDouble(place -> distanceService.calculateDistanceKm(
                        place.getLatitude(),
                        place.getLongitude(),
                        hotel.getLatitude(),
                        hotel.getLongitude()
                ))
                .average()
                .orElse(0.0);
    }

    /** 날짜별 일반 장소 ID만 모아 숙소 반복을 코스 중복 계산에서 제외한다. */
    private Map<LocalDate, Set<Long>> ordinaryPlaceIdsByDate(
            CourseOptimizeResponse optimized
    ) {
        Map<LocalDate, Set<Long>> output = new TreeMap<>();
        for (OptimizedPlaceDto place : optimized.getOptimizedPlaces()) {
            if ("HOTEL".equalsIgnoreCase(place.getCategory())) {
                continue;
            }
            output.computeIfAbsent(
                    place.getVisitDate(),
                    ignored -> new LinkedHashSet<>()
            ).add(place.getPlaceId());
        }
        return output;
    }

    private void logOverlapResult(
            Long resultId,
            OptionStrategy strategy,
            Map<LocalDate, Set<Long>> current,
            List<Map<LocalDate, Set<Long>>> previousOptions,
            int dailyOverlapLimit
    ) {
        int maximumOverlap = 0;
        for (Map<LocalDate, Set<Long>> previous : previousOptions) {
            for (Map.Entry<LocalDate, Set<Long>> entry
                    : current.entrySet()) {
                int overlap = (int) entry.getValue().stream()
                        .filter(previous.getOrDefault(
                                entry.getKey(),
                                Set.of()
                        )::contains)
                        .count();
                maximumOverlap = Math.max(maximumOverlap, overlap);
            }
        }

        if (maximumOverlap > dailyOverlapLimit) {
            log.warn(
                    "카테고리 후보 부족으로 코스 중복 상한을 완화했습니다: "
                            + "resultId={}, strategy={}, maximumDailyOverlap={}, limit={}",
                    resultId,
                    strategy,
                    maximumOverlap,
                    dailyOverlapLimit
            );
        } else {
            log.info(
                    "코스 중복 검사 완료: resultId={}, strategy={}, "
                            + "maximumDailyOverlap={}, limit={}",
                    resultId,
                    strategy,
                    maximumOverlap,
                    dailyOverlapLimit
            );
        }
    }

    /** 후보 풀 행렬에서 장소 ID를 빠르게 찾기 위한 인덱스를 만든다. */
    private Map<Long, Integer> createCandidateIndexes(
            List<PlaceCandidateDto> candidates
    ) {
        Map<Long, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            indexes.put(candidates.get(index).getPlaceId(), index);
        }
        return indexes;
    }

    /** recommendationKey의 날짜:장소ID 항목에서 장소 ID만 복원한다. */
    private Set<Long> extractPlaceIds(String recommendationKey) {
        Set<Long> placeIds = new LinkedHashSet<>();
        for (String token : recommendationKey.split("[|,]")) {
            int separatorIndex = token.lastIndexOf(':');
            if (separatorIndex < 0
                    || separatorIndex == token.length() - 1) {
                continue;
            }
            try {
                placeIds.add(Long.parseLong(
                        token.substring(separatorIndex + 1)
                ));
            } catch (NumberFormatException ignored) {
                // 구버전 또는 손상된 키는 장소 ID 제외 계산에서 건너뛴다.
            }
        }
        return placeIds;
    }

    /** 대체 후보 교체와 숙소 삽입까지 끝난 실제 표시 장소로 재추천 제외 키를 만든다. */
    private String createOptimizedCompositionSignature(
            List<OptimizedPlaceDto> places,
            TransportMode transportMode
    ) {
        String composition = places.stream()
                .sorted(Comparator
                        .comparing(OptimizedPlaceDto::getVisitDate)
                        .thenComparing(OptimizedPlaceDto::getPlaceId))
                .map(place -> place.getVisitDate()
                        + ":" + place.getPlaceId())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return transportMode.name() + ":" + composition;
    }

    /** 최적화 결과를 프론트에서 비교할 수 있는 옵션 한 건으로 변환한다. */
    private CourseOptionResponse toOptionResponse(
            int optionNo,
            OptionStrategy strategy,
            CourseOptimizeResponse optimized,
            LocalTime dailyStartTime,
            String recommendationKey
    ) {
        List<CourseDayResponse> days = toDayResponses(
                optimized.getOptimizedPlaces(),
                dailyStartTime
        );
        return CourseOptionResponse.builder()
                .optionNo(optionNo)
                .optionType(strategy.name())
                .optionName(strategy.optionName())
                .title(buildOptionTitle(
                        strategy,
                        optimized.getOptimizedPlaces()
                ))
                .description(buildOptionDescription(
                        strategy,
                        optimized,
                        days.size()
                ))
                .recommendationKey(recommendationKey)
                .placeCount(optimized.getOptimizedPlaces().size())
                .dayCount(days.size())
                .totalDistanceKm(round(
                        optimized.getTotalDistanceKm(),
                        3
                ))
                .totalTravelTimeMinutes(round(
                        optimized.getTotalTravelTimeMinutes(),
                        2
                ))
                .totalVisitTimeMinutes(
                        optimized.getTotalVisitTimeMinutes()
                )
                .totalCourseTimeMinutes(round(
                        optimized.getTotalCourseTimeMinutes(),
                        2
                ))
                .estimatedTravelTimes(
                        optimized.getEstimatedTravelTimes()
                )
                .days(days)
                .build();
    }

    private String buildOptionTitle(
            OptionStrategy strategy,
            List<OptimizedPlaceDto> places
    ) {
        return resolveCourseTheme(places) + " " + strategy.optionName();
    }

    private String buildOptionDescription(
            OptionStrategy strategy,
            CourseOptimizeResponse optimized,
            int dayCount
    ) {
        int placeCount = optimized.getOptimizedPlaces().size();
        String duration = dayCount <= 1 ? "하루" : dayCount + "일";

        return switch (strategy) {
            case PREFERENCE -> "추천 점수가 높은 " + placeCount
                    + "곳을 중심으로 취향을 가장 진하게 반영한 "
                    + duration + " 코스예요.";
            case MIN_DISTANCE -> "장소 사이 이동을 총 "
                    + round(optimized.getTotalDistanceKm(), 1)
                    + "km 동선으로 줄여 부담 없이 이어지는 "
                    + duration + " 코스예요.";
            case BALANCED -> "추천 점수와 이동 시간을 함께 고려해 볼거리와 동선의 균형을 맞춘 "
                    + duration + " 코스예요.";
        };
    }

    /** 여러 장소에 표시된 8개 테마 중 가장 많이 포함된 테마를 대표 문구로 선택한다. */
    private String resolveCourseTheme(List<OptimizedPlaceDto> places) {
        String[] labels = {
                "궁궐·문화",
                "자연·한강",
                "데이트",
                "서울 미식",
                "감성 카페",
                "쇼핑·핫플",
                "서울 야경",
                "호텔·스테이"
        };
        int[] counts = new int[labels.length];

        for (OptimizedPlaceDto place : places) {
            counts[0] += isYes(place.getThemePalaceCultureYn()) ? 1 : 0;
            counts[1] += isYes(place.getThemeNatureHangangYn()) ? 1 : 0;
            counts[2] += isYes(place.getThemeDateYn()) ? 1 : 0;
            counts[3] += isYes(place.getThemeFoodTourYn()) ? 1 : 0;
            counts[4] += isYes(place.getThemeCafeTourYn()) ? 1 : 0;
            counts[5] += isYes(
                    place.getThemeShoppingHotplaceYn()
            ) ? 1 : 0;
            counts[6] += isYes(place.getThemeNightViewYn()) ? 1 : 0;
            counts[7] += isYes(place.getThemeHotelStayYn()) ? 1 : 0;
        }

        int bestIndex = 0;
        for (int index = 1; index < counts.length; index++) {
            if (counts[index] > counts[bestIndex]) {
                bestIndex = index;
            }
        }
        if (counts[bestIndex] > 0) {
            return labels[bestIndex];
        }

        return places.stream()
                .map(OptimizedPlaceDto::getPlaceName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse("서울 맞춤");
    }

    private boolean isYes(String value) {
        return "Y".equalsIgnoreCase(value);
    }

    /** 원본 요청을 변경하지 않고 날짜와 중첩 대체 후보를 포함한 복사본을 만든다. */
    private PlaceCandidateDto copyCandidateWithDate(
            PlaceCandidateDto source,
            LocalDate visitDate,
            boolean includeAlternatives
    ) {
        if (source == null) {
            throw new IllegalArgumentException("장소 후보는 null일 수 없습니다.");
        }
        if (source.getPlaceId() == null || source.getPlaceId() < 1) {
            throw new IllegalArgumentException("장소 ID는 1 이상이어야 합니다.");
        }

        List<PlaceCandidateDto> alternatives = new ArrayList<>();
        if (includeAlternatives
                && source.getAlternativeCandidates() != null) {
            for (PlaceCandidateDto alternative
                    : source.getAlternativeCandidates()) {
                PlaceCandidateDto copiedAlternative =
                        copyCandidateWithDate(
                                alternative,
                                visitDate,
                                false
                        );
                validateCandidateForRecommendation(copiedAlternative);
                alternatives.add(copiedAlternative);
            }
        }

        return PlaceCandidateDto.builder()
                .placeId(source.getPlaceId())
                .placeName(source.getPlaceName())
                .category(normalizeCategory(source.getCategory()))
                .address(source.getAddress())
                .roadAddress(source.getRoadAddress())
                .imageUrl(source.getImageUrl())
                .recommendationScore(source.getRecommendationScore())
                .latitude(source.getLatitude())
                .longitude(source.getLongitude())
                .visitDate(visitDate)
                .themePalaceCultureYn(normalizeYn(
                        source.getThemePalaceCultureYn(),
                        "themePalaceCultureYn"
                ))
                .themeNatureHangangYn(normalizeYn(
                        source.getThemeNatureHangangYn(),
                        "themeNatureHangangYn"
                ))
                .themeDateYn(normalizeYn(
                        source.getThemeDateYn(),
                        "themeDateYn"
                ))
                .themeFoodTourYn(normalizeYn(
                        source.getThemeFoodTourYn(),
                        "themeFoodTourYn"
                ))
                .themeCafeTourYn(normalizeYn(
                        source.getThemeCafeTourYn(),
                        "themeCafeTourYn"
                ))
                .themeShoppingHotplaceYn(normalizeYn(
                        source.getThemeShoppingHotplaceYn(),
                        "themeShoppingHotplaceYn"
                ))
                .themeNightViewYn(normalizeYn(
                        source.getThemeNightViewYn(),
                        "themeNightViewYn"
                ))
                .themeHotelStayYn(normalizeYn(
                        source.getThemeHotelStayYn(),
                        "themeHotelStayYn"
                ))
                .alternativeCandidates(alternatives)
                .build();
    }

    /** TOUR·RESTAURANT·CAFE·HOTEL 외 카테고리를 요청 오류로 처리한다. */
    private String normalizeCategory(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("장소 카테고리는 필수입니다.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 장소 카테고리입니다. category=" + value
            );
        }
        return normalized;
    }

    /** 최적화된 평면 장소 목록을 프론트 공통 계약인 날짜별 구조로 변환한다. */
    private List<CourseDayResponse> toDayResponses(
            List<OptimizedPlaceDto> optimizedPlaces,
            LocalTime dailyStartTime
    ) {
        Map<LocalDate, List<OptimizedPlaceDto>> placesByDate =
                new TreeMap<>();
        for (OptimizedPlaceDto place : optimizedPlaces) {
            placesByDate.computeIfAbsent(
                    place.getVisitDate(),
                    ignored -> new ArrayList<>()
            ).add(place);
        }

        List<CourseDayResponse> days = new ArrayList<>();
        int dayNo = 1;
        for (Map.Entry<LocalDate, List<OptimizedPlaceDto>> entry
                : placesByDate.entrySet()) {
            List<OptimizedPlaceDto> dailyOptimizedPlaces =
                    entry.getValue().stream()
                            .sorted(Comparator.comparing(
                                    OptimizedPlaceDto::getVisitOrder
                            ))
                            .toList();
            List<CoursePlaceResponse> places = toPlaceResponses(
                    dailyOptimizedPlaces,
                    dailyStartTime
            );

            double dailyDistanceKm = dailyOptimizedPlaces.stream()
                    .mapToDouble(place -> valueOrZero(
                            place.getDistanceFromPreviousKm()
                    ))
                    .sum();
            double dailyTravelTimeMinutes =
                    dailyOptimizedPlaces.stream()
                            .mapToDouble(place -> valueOrZero(
                                    place.getTravelTimeFromPreviousMinutes()
                            ))
                            .sum();
            int dailyVisitTimeMinutes = dailyOptimizedPlaces.stream()
                    .mapToInt(place -> valueOrZero(
                            place.getExpectedVisitMinutes()
                    ))
                    .sum();

            days.add(CourseDayResponse.builder()
                    .dayNo(dayNo++)
                    .visitDate(entry.getKey())
                    .dailyDistanceKm(round(dailyDistanceKm, 3))
                    .dailyTravelTimeMinutes(round(
                            dailyTravelTimeMinutes,
                            2
                    ))
                    .dailyVisitTimeMinutes(dailyVisitTimeMinutes)
                    .dailyCourseTimeMinutes(round(
                            dailyVisitTimeMinutes
                                    + dailyTravelTimeMinutes,
                            2
                    ))
                    .places(places)
                    .build());
        }
        return days;
    }

    /** 공통 시작 시각부터 이동·체류시간을 누적해 장소별 예상 방문 시각을 만든다. */
    private List<CoursePlaceResponse> toPlaceResponses(
            List<OptimizedPlaceDto> places,
            LocalTime dailyStartTime
    ) {
        List<CoursePlaceResponse> responses = new ArrayList<>();
        LocalTime currentTime = dailyStartTime;

        for (OptimizedPlaceDto place : places) {
            currentTime = currentTime.plusMinutes(Math.round(
                    valueOrZero(
                            place.getTravelTimeFromPreviousMinutes()
                    )
            ));
            responses.add(toPlaceResponse(
                    place,
                    currentTime.format(
                            DateTimeFormatter.ofPattern("HH:mm")
                    )
            ));
            currentTime = currentTime.plusMinutes(valueOrZero(
                    place.getExpectedVisitMinutes()
            ));
        }
        return responses;
    }

    private CoursePlaceResponse toPlaceResponse(
            OptimizedPlaceDto place,
            String visitTime
    ) {
        return CoursePlaceResponse.builder()
                .placeId(place.getPlaceId())
                .placeName(place.getPlaceName())
                .category(place.getCategory())
                .address(place.getAddress())
                .roadAddress(place.getRoadAddress())
                .imageUrl(place.getImageUrl())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .recommendationScore(place.getRecommendationScore())
                .themePalaceCultureYn(
                        place.getThemePalaceCultureYn()
                )
                .themeNatureHangangYn(
                        place.getThemeNatureHangangYn()
                )
                .themeDateYn(place.getThemeDateYn())
                .themeFoodTourYn(place.getThemeFoodTourYn())
                .themeCafeTourYn(place.getThemeCafeTourYn())
                .themeShoppingHotplaceYn(
                        place.getThemeShoppingHotplaceYn()
                )
                .themeNightViewYn(place.getThemeNightViewYn())
                .themeHotelStayYn(place.getThemeHotelStayYn())
                .visitOrder(place.getVisitOrder())
                .visitTime(visitTime)
                .expectedVisitMinutes(
                        place.getExpectedVisitMinutes()
                )
                .distanceFromPreviousKm(
                        place.getDistanceFromPreviousKm()
                )
                .travelTimeFromPreviousMinutes(
                        place.getTravelTimeFromPreviousMinutes()
                )
                .transitPathType(place.getTransitPathType())
                .routeEstimated(place.getRouteEstimated())
                .build();
    }

    private String normalizeYn(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return "N";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("Y") && !normalized.equals("N")) {
            throw new IllegalArgumentException(
                    fieldName + "은 Y 또는 N이어야 합니다."
            );
        }
        return normalized;
    }

    private double normalizeHigher(
            double value,
            double minimum,
            double maximum
    ) {
        if (Math.abs(maximum - minimum) < EPSILON) {
            return 1.0;
        }
        return (value - minimum) / (maximum - minimum);
    }

    private double normalizeLower(
            double value,
            double minimum,
            double maximum
    ) {
        return 1.0 - normalizeHigher(value, minimum, maximum);
    }

    private double inverseCost(double value) {
        return 1.0 / (1.0 + Math.max(0.0, value));
    }

    private double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private enum OptionStrategy {
        PREFERENCE("취향 집중 코스"),
        MIN_DISTANCE("이동 최소 코스"),
        BALANCED("균형 추천 코스");

        private final String optionName;

        OptionStrategy(String optionName) {
            this.optionName = optionName;
        }

        String optionName() {
            return optionName;
        }
    }

    private record ValidatedRecommendation(
            List<ValidatedDailyPlan> dailyPlans,
            Set<String> excludedRecommendationKeys,
            TransportMode transportMode,
            Set<Long> previouslyRecommendedPlaceIds,
            List<PlaceCandidateDto> hotelCandidates
    ) {
    }

    private record ValidatedDailyPlan(
            LocalDate visitDate,
            int targetPlaceCount,
            Map<String, Integer> categoryTargets,
            List<PlaceCandidateDto> placeCandidates
    ) {
    }

    private record DailyRouteContext(
            ValidatedDailyPlan plan,
            RouteMatrix routeMatrix,
            Map<Long, Integer> candidateIndexes,
            ScoreRange scoreRange
    ) {
    }

    private record SequentialSelection(
            List<PlaceCandidateDto> placeCandidates,
            Map<LocalDate, Long> preferredFirstPlaceIdsByDate
    ) {
    }

    private record DailyPick(
            List<PlaceCandidateDto> placeCandidates,
            Long firstPlaceId,
            double recommendationScore,
            double travelTimeMinutes,
            double distanceKm,
            int previousRecommendationCount,
            int overlapExcess,
            int totalOverlap,
            String signature
    ) {
    }

    private record PickReuseMetrics(
            int previousRecommendationCount,
            int overlapExcess,
            int totalOverlap
    ) {
    }

    private record RouteEstimate(
            double distanceKm,
            double travelTimeMinutes
    ) {
    }

    private record HotelEvaluation(
            PlaceCandidateDto hotel,
            double averageDistanceKm
    ) {
    }

    private record ScoreRange(
            double minimum,
            double maximum
    ) {
        static ScoreRange from(List<PlaceCandidateDto> candidates) {
            return new ScoreRange(
                    candidates.stream()
                            .mapToDouble(candidate -> candidate
                                    .getRecommendationScore() == null
                                    ? 0.0
                                    : candidate.getRecommendationScore())
                            .min()
                            .orElse(0.0),
                    candidates.stream()
                            .mapToDouble(candidate -> candidate
                                    .getRecommendationScore() == null
                                    ? 0.0
                                    : candidate.getRecommendationScore())
                            .max()
                            .orElse(0.0)
            );
        }
    }
}
