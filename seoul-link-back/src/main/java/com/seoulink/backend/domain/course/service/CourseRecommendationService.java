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
import com.seoulink.backend.domain.course.model.TransitPathType;
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
    // 후보가 충분하면 옵션 간 장소 중복을 허용하지 않는다.
    // 후보가 부족한 경우 candidateRestrictionLevel에서만 단계적으로 재사용한다.
    private static final int STRICT_DAILY_OVERLAP_LIMIT = 0;
    private static final int MAX_DUPLICATE_RECOMMENDATION_ATTEMPTS = 3;
    private static final double PREVIOUSLY_RECOMMENDED_PENALTY = 20.0;
    private static final double NEW_PLACE_BONUS = 5.0;
    private static final double DISPLAY_SCORE_MIN = 70.0;
    private static final double DISPLAY_SCORE_MAX = 95.0;
    private static final double EQUAL_SCORE_DISPLAY_VALUE = 85.0;
    private static final double EPSILON = 0.000000001;
    private static final double WALKING_MAX_MINUTES = 20.0;
    private static final double WALKING_TARGET_AVERAGE_MINUTES = 15.0;
    private static final double WALKING_RELAXED_AVERAGE_MINUTES = 18.0;
    private static final int MAX_WALKING_REPAIR_CHANGES_PER_DAY = 3;
    private static final int MAX_WALKING_REPLACEMENT_CANDIDATES = 2;
    private static final int MAX_WALKING_ALTERNATE_DAILY_PICKS = 2;
    private static final int MIN_WALKING_PLACES_PER_DAY = 2;
    private static final int WALKING_PATH_BEAM_WIDTH = 2_500;

    // 일정 흐름 보정은 동선을 과도하게 늘리지 않는 범위에서만 적용한다.
    private static final int LUNCH_START_MINUTE = 11 * 60 + 30;
    private static final int LUNCH_END_MINUTE = 14 * 60;
    private static final int LUNCH_TARGET_MINUTE = 12 * 60 + 30;
    private static final int AFTERNOON_CAFE_MINUTE = 13 * 60;
    private static final double MAX_NATURAL_ROUTE_EXTRA_MINUTES = 15.0;
    private static final double MAX_NATURAL_ROUTE_EXTRA_RATIO = 0.20;
    private static final int MAX_NATURAL_ROUTE_PLACES = 8;

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
        Map<LocalDate, Set<Long>> usedFirstPlaceIdsByDate = new TreeMap<>();
        Set<Long> usedHotelIds = new LinkedHashSet<>();
        Set<String> generatedRecommendationKeys = new LinkedHashSet<>();
        int optionNo = 1;

        for (OptionStrategy strategy : OptionStrategy.values()) {
            boolean walking = validated.transportMode() == TransportMode.WALKING;
            Set<Long> retryAvoidPlaceIds = new LinkedHashSet<>();
            CourseOptimizeResponse optimized = null;
            String recommendationKey = null;
            Long selectedHotelId = null;
            boolean duplicateRecommendation = false;

            for (int attempt = 1;
                 attempt <= MAX_DUPLICATE_RECOMMENDATION_ATTEMPTS;
                 attempt++) {
                Set<Long> attemptPreviouslyRecommendedPlaceIds =
                        new LinkedHashSet<>(
                                validated.previouslyRecommendedPlaceIds()
                        );
                attemptPreviouslyRecommendedPlaceIds.addAll(
                        retryAvoidPlaceIds
                );

                SequentialSelection selection = walking
                        ? createWalkingSequentialSelection(
                        routeContexts,
                        strategy,
                        attemptPreviouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit,
                        request.getResultId()
                )
                        : createSequentialSelection(
                        routeContexts,
                        strategy,
                        attemptPreviouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit
                );

                if (walking) {
                    optimized = optimizeFixedWalkingSelection(
                            selection,
                            strategy,
                            request.getResultId()
                    );
                } else {
                    optimized =
                            courseOptimizationService
                                    .optimizeForRecommendation(
                                            CourseOptimizeRequest.builder()
                                                    .transportMode(
                                                            validated.transportMode()
                                                    )
                                                    .placeCandidates(
                                                            selection.placeCandidates()
                                                    )
                                                    .build(),
                                            selection
                                                    .preferredFirstPlaceIdsByDate()
                                    );
                }

                // 최종 장소 구성은 유지하면서 관광지 시작·점심 식당·카페 흐름을
                // 기존 동선이 크게 나빠지지 않는 범위에서 방문 순서에 반영한다.
                optimized = applyNaturalScheduleFlow(
                        optimized,
                        request.getDailyStartTime(),
                        routeContexts,
                        usedFirstPlaceIdsByDate
                );

                selectedHotelId = null;
                if (validated.dailyPlans().size() >= 2) {
                    HotelEvaluation selectedHotelEvaluation =
                            selectHotelCandidateForOption(
                                    validated.hotelCandidates(),
                                    optimized,
                                    strategy,
                                    attemptPreviouslyRecommendedPlaceIds,
                                    usedHotelIds
                            );
                    if (selectedHotelEvaluation != null) {
                        PlaceCandidateDto selectedHotel =
                                selectedHotelEvaluation.hotel();
                        optimized =
                                courseOptimizationService
                                        .appendFixedHotelBeforeFinalDayForRecommendation(
                                                optimized,
                                                selectedHotel
                                        );
                        if (validated.transportMode()
                                == TransportMode.WALKING
                                && !selectedHotelEvaluation.estimated()) {
                            optimized = applyActualWalkingHotelLegs(
                                    optimized,
                                    selectedHotel
                            );
                        }
                        selectedHotelId = selectedHotel.getPlaceId();
                    } else {
                        log.warn(
                                "다일 추천에 적용 가능한 숙소 후보가 없습니다: "
                                        + "resultId={}, strategy={}",
                                request.getResultId(),
                                strategy
                        );
                    }
                }

                if (walking) {
                    validateWalkingDailyTimeLimits(
                            optimized,
                            request.getResultId(),
                            strategy,
                            "최종 일정 흐름·숙소 반영"
                    );
                }

                recommendationKey =
                        createOptimizedCompositionSignature(
                                optimized.getOptimizedPlaces(),
                                validated.transportMode()
                        );
                duplicateRecommendation =
                        validated.excludedRecommendationKeys()
                                .contains(recommendationKey)
                                || generatedRecommendationKeys
                                .contains(recommendationKey);
                if (!duplicateRecommendation) {
                    break;
                }

                // 같은 구성이면 해당 코스의 장소를 재추천 장소처럼 낮은 우선순위로
                // 내려, 아직 사용하지 않은 후보가 있을 때 다음 시도에서 교체한다.
                retryAvoidPlaceIds.addAll(
                        extractPlaceIds(recommendationKey)
                );
                log.info(
                        "중복 코스 재생성 시도: resultId={}, strategy={}, "
                                + "attempt={}/{}, recommendationKey={}",
                        request.getResultId(),
                        strategy,
                        attempt,
                        MAX_DUPLICATE_RECOMMENDATION_ATTEMPTS,
                        recommendationKey
                );
            }

            if (duplicateRecommendation) {
                log.warn(
                        "다른 장소 조합을 찾지 못해 최소 중복 코스를 반환합니다: "
                                + "resultId={}, strategy={}, recommendationKey={}",
                        request.getResultId(),
                        strategy,
                        recommendationKey
                );
            }
            // 제외 키와 겹친 결과도 이번 응답 안에서는 다시 선택되지 않도록 기록한다.
            generatedRecommendationKeys.add(recommendationKey);
            if (selectedHotelId != null) {
                usedHotelIds.add(selectedHotelId);
            }
            rememberFirstPlaceIdsByDate(
                    optimized,
                    usedFirstPlaceIdsByDate
            );

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
                        + "dailyOverlapLimit={}, walkingMode={}",
                request.getResultId(),
                courseOptions.size(),
                validated.dailyPlans().size(),
                dailyOverlapLimit,
                validated.transportMode() == TransportMode.WALKING
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

    /**
     * 날짜별 후보 풀 행렬을 한 번만 생성한다.
     *
     * <p>도보는 20분 상한을 실제 경로로 강제해야 하므로 ORS 행렬을 사용하고,
     * 자동차·대중교통은 기존 호출 절약 정책대로 추정 행렬을 사용한다.</p>
     */
    private Map<LocalDate, DailyRouteContext> createDailyRouteContexts(
            List<ValidatedDailyPlan> dailyPlans,
            TransportMode transportMode
    ) {
        Map<LocalDate, DailyRouteContext> contexts = new TreeMap<>();
        for (ValidatedDailyPlan dailyPlan : dailyPlans) {
            RouteMatrix routeMatrix;
            if (transportMode == TransportMode.WALKING) {
                // 일부 장소 쌍이 추정값이어도 사용 가능한 실제 20분 이내 연결은 남길 수 있다.
                // 날짜 하나의 실패 때문에 이후 날짜 전체를 추정 행렬로 바꾸지 않는다.
                routeMatrix = distanceService.calculateRouteMatrix(
                        dailyPlan.placeCandidates(),
                        transportMode
                );
            } else {
                routeMatrix = distanceService.calculateCandidatePoolMatrix(
                        dailyPlan.placeCandidates(),
                        transportMode
                );
            }
            contexts.put(
                    dailyPlan.visitDate(),
                    new DailyRouteContext(
                            dailyPlan,
                            routeMatrix,
                            createCandidateIndexes(
                                    dailyPlan.placeCandidates()
                            ),
                            ScoreRange.from(dailyPlan.placeCandidates()),
                            RouteCostRange.from(routeMatrix)
                    )
            );
        }
        return contexts;
    }

    /**
     * 모든 일정 유형에서 옵션 간 중복 0개를 먼저 목표로 한다.
     * 후보가 부족하면 candidateRestrictionLevel의 단계적 완화로 필요한 만큼만 재사용한다.
     */
    private int resolveDailyOverlapLimit(
            String travelCode,
            List<ValidatedDailyPlan> dailyPlans
    ) {
        return STRICT_DAILY_OVERLAP_LIMIT;
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
     * 도보 추천은 실제 20분 이내 간선만 따라가며 목표 장소 수를 모두 채운 경로를 먼저 찾는다.
     * 자동차·대중교통의 기존 탐욕 선발은 이 메서드를 사용하지 않는다.
     */
    private SequentialSelection createWalkingSequentialSelection(
            Map<LocalDate, DailyRouteContext> routeContexts,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            Long resultId
    ) {
        List<PlaceCandidateDto> selectedCandidates = new ArrayList<>();
        Map<LocalDate, Long> preferredFirstPlaceIds = new TreeMap<>();

        for (Map.Entry<LocalDate, DailyRouteContext> entry
                : routeContexts.entrySet()) {
            DailyPick dailyPick = selectWalkingDailyPlaces(
                    entry.getValue(),
                    strategy,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit,
                    resultId
            );
            Set<Long> selectedIds = dailyPick.placeCandidates().stream()
                    .map(PlaceCandidateDto::getPlaceId)
                    .collect(java.util.stream.Collectors.toSet());
            Set<Long> blockedAlternativeIds = createBlockedAlternativeIds(
                    entry.getKey(),
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces
            );

            // beam search가 찾은 순서가 이미 실제 도보 20분 이내 경로이므로 순서를 유지한다.
            for (PlaceCandidateDto candidate : dailyPick.placeCandidates()) {
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
     * 도보 코스는 모든 인접 구간이 실제 20분 이내인 경로만 허용한다.
     *
     * <p>같은 장소 수에서는 먼저 총 도보시간이 구간 수×15분 이하인 경로를 찾고,
     * 없을 때만 구간 수×18분 이하로 완화한다. 두 기준으로도 만들 수 없으면
     * 장소 수를 줄여 다시 시도하며, 구간당 20분 상한과 실제 경로 조건은
     * 끝까지 완화하지 않는다.</p>
     */
    private DailyPick selectWalkingDailyPlaces(
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            Long resultId
    ) {
        int requestedPlaceCount = context.plan().targetPlaceCount();
        int minimumPlaceCount = Math.min(
                requestedPlaceCount,
                MIN_WALKING_PLACES_PER_DAY
        );

        for (int placeCount = requestedPlaceCount;
             placeCount >= minimumPlaceCount;
             placeCount--) {
            List<Map<String, Integer>> targetVariants =
                    createWalkingCategoryTargetVariants(
                            context,
                            placeCount
                    );
            Map<String, Integer> idealTargets =
                    deriveFinalCategoryTargets(
                            context.plan().categoryTargets(),
                            placeCount
                    );

            DailyPick strictPick = findWalkingPickWithinAverage(
                    context,
                    strategy,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit,
                    placeCount,
                    targetVariants,
                    WALKING_TARGET_AVERAGE_MINUTES
            );
            if (strictPick != null) {
                return logAndReturnWalkingPick(
                        strictPick,
                        context,
                        strategy,
                        resultId,
                        requestedPlaceCount,
                        placeCount,
                        idealTargets,
                        WALKING_TARGET_AVERAGE_MINUTES
                );
            }

            DailyPick relaxedPick = findWalkingPickWithinAverage(
                    context,
                    strategy,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit,
                    placeCount,
                    targetVariants,
                    WALKING_RELAXED_AVERAGE_MINUTES
            );
            if (relaxedPick != null) {
                return logAndReturnWalkingPick(
                        relaxedPick,
                        context,
                        strategy,
                        resultId,
                        requestedPlaceCount,
                        placeCount,
                        idealTargets,
                        WALKING_RELAXED_AVERAGE_MINUTES
                );
            }
        }

        throw new IllegalStateException(
                "실제 도보 20분 이내이면서 평균 18분 이하인 코스를 만들 수 없습니다. "
                        + "resultId=" + resultId
                        + ", strategy=" + strategy
                        + ", visitDate=" + context.plan().visitDate()
                        + ", requestedPlaces=" + requestedPlaceCount
                        + ", minimumPlaces=" + minimumPlaceCount
        );
    }

    /** 같은 장소 수와 시간 단계에서 카테고리 배분 후보를 순서대로 검사한다. */
    private DailyPick findWalkingPickWithinAverage(
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            int placeCount,
            List<Map<String, Integer>> targetVariants,
            double maximumAverageMinutes
    ) {
        double maximumTotalMinutes = walkingTotalTimeLimit(
                placeCount,
                maximumAverageMinutes
        );
        for (Map<String, Integer> categoryTargets : targetVariants) {
            DailyPick pick = findWalkingDailyPlaces(
                    context,
                    strategy,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit,
                    placeCount,
                    categoryTargets,
                    maximumTotalMinutes
            );
            if (pick != null) {
                return pick;
            }
        }
        return null;
    }

    private DailyPick logAndReturnWalkingPick(
            DailyPick pick,
            DailyRouteContext context,
            OptionStrategy strategy,
            Long resultId,
            int requestedPlaceCount,
            int selectedPlaceCount,
            Map<String, Integer> idealTargets,
            double maximumAverageMinutes
    ) {
        if (selectedPlaceCount < requestedPlaceCount) {
            log.warn(
                    "도보 제한을 지키기 위해 하루 장소 수를 줄였습니다: "
                            + "resultId={}, strategy={}, visitDate={}, "
                            + "requestedPlaces={}, selectedPlaces={}",
                    resultId,
                    strategy,
                    context.plan().visitDate(),
                    requestedPlaceCount,
                    selectedPlaceCount
            );
        }

        Map<String, Integer> selectedTargets = countCategories(
                pick.placeCandidates()
        );
        if (!selectedTargets.equals(idealTargets)) {
            log.warn(
                    "도보 연결 가능한 장소를 확보하기 위해 카테고리 배분을 완화했습니다: "
                            + "resultId={}, strategy={}, visitDate={}, "
                            + "idealTargets={}, selectedTargets={}",
                    resultId,
                    strategy,
                    context.plan().visitDate(),
                    idealTargets,
                    selectedTargets
            );
        }

        int legCount = Math.max(0, pick.placeCandidates().size() - 1);
        double averageMinutes = legCount == 0
                ? 0.0
                : pick.travelTimeMinutes() / legCount;
        log.info(
                "도보 경로 생성 완료: resultId={}, strategy={}, visitDate={}, "
                        + "places={}, totalMinutes={}, averageMinutes={}, "
                        + "maximumMinutes={}, averageLimit={}, legLimit={}",
                resultId,
                strategy,
                context.plan().visitDate(),
                pick.placeCandidates().size(),
                round(pick.travelTimeMinutes(), 1),
                round(averageMinutes, 1),
                round(maximumWalkingLegMinutes(
                        pick.placeCandidates(),
                        context
                ), 1),
                maximumAverageMinutes,
                WALKING_MAX_MINUTES
        );
        return pick;
    }

    private Map<String, Integer> countCategories(
            List<PlaceCandidateDto> candidates
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String category : SUPPORTED_CATEGORIES) {
            counts.put(category, 0);
        }
        for (PlaceCandidateDto candidate : candidates) {
            counts.computeIfPresent(
                    candidate.getCategory(),
                    (ignored, count) -> count + 1
            );
        }
        return Map.copyOf(counts);
    }

    private double walkingTotalTimeLimit(
            int placeCount,
            double maximumAverageMinutes
    ) {
        return Math.max(0, placeCount - 1) * maximumAverageMinutes;
    }

    /** 지정한 장소 수·카테고리 배분·총시간 상한으로 실제 도보 경로를 찾는다. */
    private DailyPick findWalkingDailyPlaces(
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            int targetPlaceCount,
            Map<String, Integer> categoryTargets,
            double maximumTotalMinutes
    ) {
        List<WalkingPathState> states = new ArrayList<>();
        for (PlaceCandidateDto first : context.plan().placeCandidates()) {
            if (categoryTargets.getOrDefault(first.getCategory(), 0) < 1) {
                continue;
            }
            Map<String, Integer> remainingTargets =
                    new LinkedHashMap<>(categoryTargets);
            remainingTargets.put(
                    first.getCategory(),
                    remainingTargets.get(first.getCategory()) - 1
            );
            Set<Long> selectedIds = new LinkedHashSet<>();
            selectedIds.add(first.getPlaceId());
            WalkingPathState state = new WalkingPathState(
                    List.of(first),
                    Set.copyOf(selectedIds),
                    Map.copyOf(remainingTargets),
                    valueOrZero(first.getRecommendationScore()),
                    0.0,
                    0.0
            );
            if (canCompleteWalkingPath(
                    state,
                    context,
                    targetPlaceCount,
                    maximumTotalMinutes
            )) {
                states.add(state);
            }
        }
        if (states.isEmpty()) {
            return null;
        }

        states = retainBestWalkingStates(
                states,
                context,
                strategy,
                previouslyRecommendedPlaceIds,
                generatedOptionPlaces,
                dailyOverlapLimit
        );

        boolean beamSearchExhausted = false;
        for (int depth = 1; depth < targetPlaceCount; depth++) {
            List<WalkingPathState> expanded = new ArrayList<>();
            for (WalkingPathState state : states) {
                List<PlaceCandidateDto> nextCandidates =
                        walkingExpansionCandidates(
                                state,
                                context,
                                strategy,
                                previouslyRecommendedPlaceIds,
                                generatedOptionPlaces,
                                dailyOverlapLimit
                        );
                for (PlaceCandidateDto next : nextCandidates) {
                    WalkingPathState nextState = extendWalkingPath(
                            state,
                            next,
                            context
                    );
                    if (canCompleteWalkingPath(
                            nextState,
                            context,
                            targetPlaceCount,
                            maximumTotalMinutes
                    )) {
                        expanded.add(nextState);
                    }
                }
            }
            if (expanded.isEmpty()) {
                beamSearchExhausted = true;
                break;
            }
            states = retainBestWalkingStates(
                    expanded,
                    context,
                    strategy,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit
            );
        }

        if (!beamSearchExhausted) {
            DailyPick beamResult = states.stream()
                    .filter(state -> state.path().size() == targetPlaceCount)
                    .filter(this::walkingTargetsSatisfied)
                    .filter(state -> state.travelTimeMinutes()
                            <= maximumTotalMinutes + EPSILON)
                    .map(state -> toWalkingDailyPick(
                            state,
                            context,
                            previouslyRecommendedPlaceIds,
                            generatedOptionPlaces,
                            dailyOverlapLimit
                    ))
                    .min((left, right) -> compareDailyPicks(
                            left,
                            right,
                            strategy,
                            context
                    ))
                    .orElse(null);
            if (beamResult != null) {
                return beamResult;
            }
        }

        // 빔 폭 때문에 가능한 경로가 잘리는 경우를 막기 위한 정확 탐색이다.
        return findWalkingDailyPlacesDepthFirst(
                context,
                strategy,
                previouslyRecommendedPlaceIds,
                generatedOptionPlaces,
                dailyOverlapLimit,
                targetPlaceCount,
                categoryTargets,
                maximumTotalMinutes
        );
    }

    private DailyPick findWalkingDailyPlacesDepthFirst(
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            int targetPlaceCount,
            Map<String, Integer> categoryTargets,
            double maximumTotalMinutes
    ) {
        List<WalkingPathState> initialStates = new ArrayList<>();
        for (PlaceCandidateDto first : context.plan().placeCandidates()) {
            if (categoryTargets.getOrDefault(first.getCategory(), 0) < 1) {
                continue;
            }
            Map<String, Integer> remainingTargets =
                    new LinkedHashMap<>(categoryTargets);
            remainingTargets.put(
                    first.getCategory(),
                    remainingTargets.get(first.getCategory()) - 1
            );
            WalkingPathState state = new WalkingPathState(
                    List.of(first),
                    Set.of(first.getPlaceId()),
                    Map.copyOf(remainingTargets),
                    valueOrZero(first.getRecommendationScore()),
                    0.0,
                    0.0
            );
            if (canCompleteWalkingPath(
                    state,
                    context,
                    targetPlaceCount,
                    maximumTotalMinutes
            )) {
                initialStates.add(state);
            }
        }
        initialStates = retainBestWalkingStates(
                initialStates,
                context,
                strategy,
                previouslyRecommendedPlaceIds,
                generatedOptionPlaces,
                dailyOverlapLimit
        );

        Set<String> deadStates = new HashSet<>();
        for (WalkingPathState initialState : initialStates) {
            WalkingPathState completed = completeWalkingPathDepthFirst(
                    initialState,
                    context,
                    strategy,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit,
                    targetPlaceCount,
                    maximumTotalMinutes,
                    deadStates
            );
            if (completed != null) {
                return toWalkingDailyPick(
                        completed,
                        context,
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit
                );
            }
        }
        return null;
    }

    private WalkingPathState completeWalkingPathDepthFirst(
            WalkingPathState state,
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            int targetPlaceCount,
            double maximumTotalMinutes,
            Set<String> deadStates
    ) {
        if (state.path().size() == targetPlaceCount) {
            return walkingTargetsSatisfied(state)
                    && state.travelTimeMinutes()
                    <= maximumTotalMinutes + EPSILON
                    ? state
                    : null;
        }

        String stateKey = walkingStateKey(state);
        if (!deadStates.add(stateKey)) {
            return null;
        }
        for (PlaceCandidateDto next : walkingExpansionCandidates(
                state,
                context,
                strategy,
                previouslyRecommendedPlaceIds,
                generatedOptionPlaces,
                dailyOverlapLimit
        )) {
            WalkingPathState nextState = extendWalkingPath(
                    state,
                    next,
                    context
            );
            if (!canCompleteWalkingPath(
                    nextState,
                    context,
                    targetPlaceCount,
                    maximumTotalMinutes
            )) {
                continue;
            }
            WalkingPathState completed = completeWalkingPathDepthFirst(
                    nextState,
                    context,
                    strategy,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit,
                    targetPlaceCount,
                    maximumTotalMinutes,
                    deadStates
            );
            if (completed != null) {
                return completed;
            }
        }
        return null;
    }

    /**
     * 같은 장소 수에서 원래 비율과 가까운 카테고리 배분부터 모두 만든다.
     * 특정 배분 하나 때문에 실제로 존재하는 도보 경로를 놓치지 않게 한다.
     */
    private List<Map<String, Integer>> createWalkingCategoryTargetVariants(
            DailyRouteContext context,
            int targetPlaceCount
    ) {
        Map<String, Integer> idealTargets = deriveFinalCategoryTargets(
                context.plan().categoryTargets(),
                targetPlaceCount
        );
        Map<String, Integer> availableByCategory = new LinkedHashMap<>();
        for (String category : SUPPORTED_CATEGORIES) {
            int available = (int) context.plan().placeCandidates().stream()
                    .filter(candidate -> category.equals(candidate.getCategory()))
                    .count();
            availableByCategory.put(category, available);
        }

        List<Map<String, Integer>> variants = new ArrayList<>();
        buildWalkingCategoryTargetVariants(
                0,
                targetPlaceCount,
                new LinkedHashMap<>(),
                context.plan().categoryTargets(),
                availableByCategory,
                variants
        );
        variants.sort(Comparator
                .comparingInt((Map<String, Integer> targets) ->
                        missingWalkingCategoryCoverage(
                                targets,
                                context.plan().categoryTargets(),
                                targetPlaceCount
                        ))
                .thenComparingInt(targets -> categoryTargetDistance(
                        targets,
                        idealTargets
                ))
                .thenComparing(this::categoryTargetSignature));
        return variants;
    }

    private void buildWalkingCategoryTargetVariants(
            int categoryIndex,
            int remaining,
            Map<String, Integer> current,
            Map<String, Integer> requestedTargets,
            Map<String, Integer> availableByCategory,
            List<Map<String, Integer>> output
    ) {
        if (categoryIndex == SUPPORTED_CATEGORIES.size()) {
            if (remaining == 0) {
                output.add(Map.copyOf(new LinkedHashMap<>(current)));
            }
            return;
        }

        String category = SUPPORTED_CATEGORIES.get(categoryIndex);
        boolean requested = requestedTargets.getOrDefault(category, 0) > 0;
        int maximum = requested
                ? Math.min(remaining, availableByCategory.getOrDefault(category, 0))
                : 0;
        for (int count = maximum; count >= 0; count--) {
            current.put(category, count);
            buildWalkingCategoryTargetVariants(
                    categoryIndex + 1,
                    remaining - count,
                    current,
                    requestedTargets,
                    availableByCategory,
                    output
            );
        }
        current.remove(category);
    }

    private int missingWalkingCategoryCoverage(
            Map<String, Integer> targets,
            Map<String, Integer> requestedTargets,
            int targetPlaceCount
    ) {
        long requestedCategoryCount = SUPPORTED_CATEGORIES.stream()
                .filter(category -> requestedTargets.getOrDefault(category, 0) > 0)
                .count();
        if (targetPlaceCount < requestedCategoryCount) {
            return 0;
        }
        return (int) SUPPORTED_CATEGORIES.stream()
                .filter(category -> requestedTargets.getOrDefault(category, 0) > 0)
                .filter(category -> targets.getOrDefault(category, 0) == 0)
                .count();
    }

    private int categoryTargetDistance(
            Map<String, Integer> left,
            Map<String, Integer> right
    ) {
        return SUPPORTED_CATEGORIES.stream()
                .mapToInt(category -> Math.abs(
                        left.getOrDefault(category, 0)
                                - right.getOrDefault(category, 0)
                ))
                .sum();
    }

    private String categoryTargetSignature(Map<String, Integer> targets) {
        return SUPPORTED_CATEGORIES.stream()
                .map(category -> String.format(
                        Locale.ROOT,
                        "%02d",
                        targets.getOrDefault(category, 0)
                ))
                .reduce((left, right) -> left + ":" + right)
                .orElse("");
    }

    private List<WalkingPathState> retainBestWalkingStates(
            List<WalkingPathState> candidates,
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        candidates.sort((left, right) -> {
            DailyPick leftPick = toWalkingDailyPick(
                    left,
                    context,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit
            );
            DailyPick rightPick = toWalkingDailyPick(
                    right,
                    context,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit
            );
            int comparison = compareDailyPicks(
                    leftPick,
                    rightPick,
                    strategy,
                    context
            );
            return comparison != 0
                    ? comparison
                    : walkingOrderedSignature(left.path()).compareTo(
                    walkingOrderedSignature(right.path())
            );
        });

        Map<String, WalkingPathState> unique = new LinkedHashMap<>();
        for (WalkingPathState state : candidates) {
            unique.putIfAbsent(walkingStateKey(state), state);
            if (unique.size() >= WALKING_PATH_BEAM_WIDTH) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<PlaceCandidateDto> walkingExpansionCandidates(
            WalkingPathState state,
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        PlaceCandidateDto current = state.path().get(
                state.path().size() - 1
        );
        List<PlaceCandidateDto> available = context.plan()
                .placeCandidates().stream()
                .filter(candidate -> !state.selectedIds().contains(
                        candidate.getPlaceId()
                ))
                .filter(candidate -> state.remainingTargets().getOrDefault(
                        candidate.getCategory(),
                        0
                ) > 0)
                .filter(candidate -> isActualWalkingEdgeWithinLimit(
                        current,
                        candidate,
                        context
                ))
                .sorted((left, right) -> compareWalkingExpansionCandidates(
                        left,
                        right,
                        state,
                        context,
                        strategy,
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit
                ))
                .toList();

        return available;
    }

    private int compareWalkingExpansionCandidates(
            PlaceCandidateDto left,
            PlaceCandidateDto right,
            WalkingPathState state,
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        int comparison = Integer.compare(
                candidateRestrictionLevel(
                        left,
                        state.path(),
                        context.plan().visitDate(),
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit
                ),
                candidateRestrictionLevel(
                        right,
                        state.path(),
                        context.plan().visitDate(),
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit
                )
        );
        if (comparison != 0) {
            return comparison;
        }

        PlaceCandidateDto current = state.path().get(
                state.path().size() - 1
        );
        RouteEstimate leftLeg = routeBetween(current, left, context);
        RouteEstimate rightLeg = routeBetween(current, right, context);

        if (strategy == OptionStrategy.PREFERENCE) {
            comparison = Double.compare(
                    valueOrZero(right.getRecommendationScore()),
                    valueOrZero(left.getRecommendationScore())
            );
            if (comparison == 0) {
                comparison = Double.compare(
                        leftLeg.travelTimeMinutes(),
                        rightLeg.travelTimeMinutes()
                );
            }
        } else if (strategy == OptionStrategy.MIN_DISTANCE) {
            comparison = Double.compare(
                    leftLeg.travelTimeMinutes(),
                    rightLeg.travelTimeMinutes()
            );
            if (comparison == 0) {
                comparison = Double.compare(
                        leftLeg.distanceKm(),
                        rightLeg.distanceKm()
                );
            }
        } else {
            double leftUtility = normalizeHigher(
                    valueOrZero(left.getRecommendationScore()),
                    context.scoreRange().minimum(),
                    context.scoreRange().maximum()
            ) * 0.50
                    + normalizeRouteCost(
                            leftLeg.travelTimeMinutes(),
                            context.routeCostRange().minimumTravelTimeMinutes(),
                            context.routeCostRange().maximumTravelTimeMinutes()
                    ) * 0.30
                    + normalizeRouteCost(
                            leftLeg.distanceKm(),
                            context.routeCostRange().minimumDistanceKm(),
                            context.routeCostRange().maximumDistanceKm()
                    ) * 0.20;
            double rightUtility = normalizeHigher(
                    valueOrZero(right.getRecommendationScore()),
                    context.scoreRange().minimum(),
                    context.scoreRange().maximum()
            ) * 0.50
                    + normalizeRouteCost(
                            rightLeg.travelTimeMinutes(),
                            context.routeCostRange().minimumTravelTimeMinutes(),
                            context.routeCostRange().maximumTravelTimeMinutes()
                    ) * 0.30
                    + normalizeRouteCost(
                            rightLeg.distanceKm(),
                            context.routeCostRange().minimumDistanceKm(),
                            context.routeCostRange().maximumDistanceKm()
                    ) * 0.20;
            comparison = Double.compare(rightUtility, leftUtility);
        }
        return comparison != 0
                ? comparison
                : left.getPlaceId().compareTo(right.getPlaceId());
    }

    private WalkingPathState extendWalkingPath(
            WalkingPathState state,
            PlaceCandidateDto next,
            DailyRouteContext context
    ) {
        PlaceCandidateDto current = state.path().get(
                state.path().size() - 1
        );
        RouteEstimate leg = routeBetween(current, next, context);
        List<PlaceCandidateDto> path = new ArrayList<>(state.path());
        path.add(next);
        Set<Long> selectedIds = new LinkedHashSet<>(state.selectedIds());
        selectedIds.add(next.getPlaceId());
        Map<String, Integer> remainingTargets =
                new LinkedHashMap<>(state.remainingTargets());
        remainingTargets.put(
                next.getCategory(),
                remainingTargets.get(next.getCategory()) - 1
        );
        return new WalkingPathState(
                List.copyOf(path),
                Set.copyOf(selectedIds),
                Map.copyOf(remainingTargets),
                state.recommendationScore()
                        + valueOrZero(next.getRecommendationScore()),
                state.travelTimeMinutes() + leg.travelTimeMinutes(),
                state.distanceKm() + leg.distanceKm()
        );
    }

    private boolean canCompleteWalkingPath(
            WalkingPathState state,
            DailyRouteContext context,
            int targetPlaceCount,
            double maximumTotalMinutes
    ) {
        if (state.path().size() > targetPlaceCount
                || state.travelTimeMinutes()
                > maximumTotalMinutes + EPSILON) {
            return false;
        }
        for (Map.Entry<String, Integer> target
                : state.remainingTargets().entrySet()) {
            int remaining = target.getValue();
            if (remaining < 0) {
                return false;
            }
            long available = context.plan().placeCandidates().stream()
                    .filter(candidate -> candidate.getCategory().equals(
                            target.getKey()
                    ))
                    .filter(candidate -> !state.selectedIds().contains(
                            candidate.getPlaceId()
                    ))
                    .count();
            if (available < remaining) {
                return false;
            }
        }
        return true;
    }

    private boolean walkingTargetsSatisfied(WalkingPathState state) {
        return state.remainingTargets().values().stream()
                .allMatch(value -> value == 0);
    }

    private boolean isActualWalkingEdgeWithinLimit(
            PlaceCandidateDto from,
            PlaceCandidateDto to,
            DailyRouteContext context
    ) {
        Integer fromIndex = context.candidateIndexes().get(
                from.getPlaceId()
        );
        Integer toIndex = context.candidateIndexes().get(to.getPlaceId());
        if (fromIndex == null || toIndex == null) {
            return false;
        }
        return !context.routeMatrix().isEstimated(fromIndex, toIndex)
                && context.routeMatrix().getTravelTimeMinutes(
                fromIndex,
                toIndex
        ) <= WALKING_MAX_MINUTES + EPSILON;
    }

    private RouteEstimate routeBetween(
            PlaceCandidateDto from,
            PlaceCandidateDto to,
            DailyRouteContext context
    ) {
        int fromIndex = context.candidateIndexes().get(from.getPlaceId());
        int toIndex = context.candidateIndexes().get(to.getPlaceId());
        return new RouteEstimate(
                context.routeMatrix().getDistanceKm(fromIndex, toIndex),
                context.routeMatrix().getTravelTimeMinutes(fromIndex, toIndex)
        );
    }

    private DailyPick toWalkingDailyPick(
            WalkingPathState state,
            DailyRouteContext context,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        PickReuseMetrics reuseMetrics = calculatePickReuseMetrics(
                state.path(),
                context.plan().visitDate(),
                previouslyRecommendedPlaceIds,
                generatedOptionPlaces,
                dailyOverlapLimit
        );
        return new DailyPick(
                List.copyOf(state.path()),
                state.path().get(0).getPlaceId(),
                state.recommendationScore(),
                state.travelTimeMinutes(),
                state.distanceKm(),
                reuseMetrics.previousRecommendationCount(),
                reuseMetrics.overlapExcess(),
                reuseMetrics.totalOverlap(),
                state.path().stream()
                        .map(PlaceCandidateDto::getPlaceId)
                        .sorted()
                        .map(String::valueOf)
                        .reduce((left, right) -> left + "," + right)
                        .orElse("")
        );
    }

    private String walkingStateKey(WalkingPathState state) {
        String selected = state.selectedIds().stream()
                .sorted()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        Long lastPlaceId = state.path().get(
                state.path().size() - 1
        ).getPlaceId();
        return lastPlaceId + "|" + selected + "|"
                + state.remainingTargets() + "|"
                + Math.round(state.travelTimeMinutes() * 1_000.0);
    }

    private String walkingOrderedSignature(
            List<PlaceCandidateDto> path
    ) {
        return path.stream()
                .map(PlaceCandidateDto::getPlaceId)
                .map(String::valueOf)
                .reduce((left, right) -> left + ">" + right)
                .orElse("");
    }

    private double maximumWalkingLegMinutes(
            List<PlaceCandidateDto> path,
            DailyRouteContext context
    ) {
        double maximum = 0.0;
        for (int index = 1; index < path.size(); index++) {
            maximum = Math.max(
                    maximum,
                    routeBetween(
                            path.get(index - 1),
                            path.get(index),
                            context
                    ).travelTimeMinutes()
            );
        }
        return maximum;
    }

    /**
     * 찾은 도보 순서를 다시 섞지 않고 실제 경로값을 응답 DTO에 적용한다.
     * 실제값이 아닌 구간, 20분 초과 구간, 평균 18분 초과 DAY는 반환하지 않는다.
     */
    private CourseOptimizeResponse optimizeFixedWalkingSelection(
            SequentialSelection selection,
            OptionStrategy strategy,
            Long resultId
    ) {
        CourseOptimizeResponse optimized =
                courseOptimizationService.resolveFixedRouteDetails(
                        CourseOptimizeRequest.builder()
                                .transportMode(TransportMode.WALKING)
                                .placeCandidates(selection.placeCandidates())
                                .build()
                );
        WalkingRouteQuality quality = walkingRouteQuality(optimized);
        if (optimized.getOptimizedPlaces().size()
                != selection.placeCandidates().size()) {
            throw new IllegalStateException(
                    "도보 고정 경로의 장소 수 검증에 실패했습니다. "
                            + "resultId=" + resultId
                            + ", strategy=" + strategy
                            + ", selectedPlaces="
                            + selection.placeCandidates().size()
                            + ", optimizedPlaces="
                            + optimized.getOptimizedPlaces().size()
            );
        }
        if (quality.violationCount() > 0
                || quality.estimatedLegCount() > 0) {
            throw new IllegalStateException(
                    "도보 하드 제한을 통과하지 못한 경로는 반환할 수 없습니다. "
                            + "resultId=" + resultId
                            + ", strategy=" + strategy
                            + ", overLimitOrEstimatedLegs="
                            + quality.violationCount()
                            + ", estimatedLegs="
                            + quality.estimatedLegCount()
                            + ", maximumMinutes="
                            + round(quality.maximumTravelMinutes(), 1)
            );
        }
        validateWalkingDailyTimeLimits(
                optimized,
                resultId,
                strategy,
                "고정 경로 상세 계산"
        );
        log.info(
                "도보 코스 하드 제한 최종 검증 완료: resultId={}, strategy={}, "
                        + "places={}, remainingViolations=0, maximumMinutes={}, "
                        + "thresholdMinutes={}",
                resultId,
                strategy,
                optimized.getOptimizedPlaces().size(),
                round(quality.maximumTravelMinutes(), 1),
                WALKING_MAX_MINUTES
        );
        return optimized;
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
                    context
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

        // 모든 전략에서 앞 옵션에 덜 사용된 장소를 먼저 고른다.
        // 이동 최소 코스도 후보가 충분할 때는 동선보다 다양성을 우선한다.
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
        if (strategy == OptionStrategy.MIN_DISTANCE) {
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
        double travelEfficiency = normalizeRouteCost(
                context.routeMatrix().getTravelTimeMinutes(
                        currentIndex,
                        candidateIndex
                ),
                context.routeCostRange().minimumTravelTimeMinutes(),
                context.routeCostRange().maximumTravelTimeMinutes()
        );
        double distanceEfficiency = normalizeRouteCost(
                context.routeMatrix().getDistanceKm(
                        currentIndex,
                        candidateIndex
                ),
                context.routeCostRange().minimumDistanceKm(),
                context.routeCostRange().maximumDistanceKm()
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
            DailyRouteContext context
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
        // 모든 전략에서 이전 옵션 전체와의 총 중복 수를 품질 점수보다 먼저 줄인다.
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
                    dailyBalancedUtility(right, context),
                    dailyBalancedUtility(left, context)
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
            DailyRouteContext context
    ) {
        double averageScore = pick.placeCandidates().isEmpty()
                ? 0.0
                : pick.recommendationScore()
                  / pick.placeCandidates().size();
        double preference = normalizeHigher(
                averageScore,
                context.scoreRange().minimum(),
                context.scoreRange().maximum()
        );
        int legCount = Math.max(0, pick.placeCandidates().size() - 1);
        double travelEfficiency = normalizeRouteCost(
                pick.travelTimeMinutes(),
                context.routeCostRange().minimumTravelTimeMinutes() * legCount,
                context.routeCostRange().maximumTravelTimeMinutes() * legCount
        );
        double distanceEfficiency = normalizeRouteCost(
                pick.distanceKm(),
                context.routeCostRange().minimumDistanceKm() * legCount,
                context.routeCostRange().maximumDistanceKm() * legCount
        );
        return preference * 0.50
                + travelEfficiency * 0.30
                + distanceEfficiency * 0.20;
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
            // 세 번째 옵션의 중복이 특정 이전 옵션에 몰리지 않도록
            // 옵션 한 쌍에서 발생하는 최대 초과 중복을 우선 최소화한다.
            overlapExcess = Math.max(
                    overlapExcess,
                    Math.max(0, overlap - dailyOverlapLimit)
            );
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

    /**
     * 모든 도보 추천 옵션을 실제 경로 기준으로 재정렬하고 20분 상한을 강제한다.
     *
     * <p>실제 20분 초과 구간은 당일 미선택 후보로 먼저 교체한다. 후보 전체로도
     * 해결할 수 없으면 장거리 구간의 원인이 되는 장소를 제외해, 먼 구간이 최종 응답에
     * 포함되는 일을 막는다.</p>
     */
    private CourseOptimizeResponse validateAndRepairWalkingOption(
            SequentialSelection selection,
            Map<LocalDate, DailyRouteContext> routeContexts,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            OptionStrategy strategy,
            Long resultId
    ) {
        boolean actualRoutesUnavailable = routeContexts.values().stream()
                .map(DailyRouteContext::routeMatrix)
                .anyMatch(RouteMatrix::estimatedTravelTimes);
        if (actualRoutesUnavailable) {
            throw new IllegalStateException(
                    "실제 도보 경로를 확인할 수 없어 추천 코스를 만들지 않았습니다. "
                            + "잠시 후 다시 시도해 주세요. resultId=" + resultId
            );
        }

        List<PlaceCandidateDto> selected =
                selection.placeCandidates().stream()
                        .map(candidate -> copyCandidateWithDate(
                                candidate,
                                candidate.getVisitDate(),
                                false
                        ))
                        .collect(java.util.stream.Collectors.toCollection(
                                ArrayList::new
                        ));
        Map<LocalDate, Long> preferredFirstPlaceIds =
                new TreeMap<>(selection.preferredFirstPlaceIdsByDate());
        Set<String> triedDailyPickSignatures = new LinkedHashSet<>();
        for (LocalDate visitDate : routeContexts.keySet()) {
            triedDailyPickSignatures.add(
                    visitDate + "|" + dailySelectionSignature(
                            selected,
                            visitDate
                    )
            );
        }

        CourseOptimizeResponse optimized =
                optimizeWalkingWithActualRoutes(
                        selected,
                        preferredFirstPlaceIds
                );
        WalkingRouteQuality quality = walkingRouteQuality(optimized);
        if (quality.actualLegCount() == 0
                && quality.estimatedLegCount() > 0) {
            throw new IllegalStateException(
                    "실제 도보 경로를 확인할 수 없어 추천 코스를 만들지 않았습니다. "
                            + "잠시 후 다시 시도해 주세요. resultId=" + resultId
            );
        }

        CourseOptimizeResponse best = optimized;
        WalkingRouteQuality bestQuality = quality;
        int actualOptimizationPasses = 1;
        int replacements = 0;
        Map<LocalDate, Integer> alternateDailyPicksByDate =
                new TreeMap<>();
        Set<String> exhaustedViolationKeys = new LinkedHashSet<>();
        int maximumChanges = Math.max(1, routeContexts.size())
                * MAX_WALKING_REPAIR_CHANGES_PER_DAY;
        int inspectionBudget = Math.max(
                selected.size(),
                maximumChanges
        ) + maximumChanges;

        for (int inspection = 0;
             inspection < inspectionBudget
                     && replacements
                     + totalValues(alternateDailyPicksByDate)
                     < maximumChanges;
             inspection++) {
            WalkingViolation violation =
                    findWalkingViolation(
                            optimized,
                            exhaustedViolationKeys
                    );
            if (violation == null) {
                break;
            }

            WalkingReplacement replacement = findWalkingReplacement(
                    violation,
                    selected,
                    routeContexts.get(violation.visitDate()),
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit
            );
            if (replacement != null) {
                replaceSelectedCandidate(
                        selected,
                        violation.visitDate(),
                        replacement.replacedPlaceId(),
                        replacement.candidate()
                );
                if (replacement.replacedPlaceId().equals(
                        preferredFirstPlaceIds.get(
                                violation.visitDate()
                        )
                )) {
                    preferredFirstPlaceIds.put(
                            violation.visitDate(),
                            replacement.candidate().getPlaceId()
                    );
                }
                triedDailyPickSignatures.add(
                        violation.visitDate() + "|"
                                + dailySelectionSignature(
                                selected,
                                violation.visitDate()
                        )
                );
                replacements++;
            } else if (alternateDailyPicksByDate.getOrDefault(
                    violation.visitDate(),
                    0
            )
                    < MAX_WALKING_ALTERNATE_DAILY_PICKS) {
                DailyPick nextPick =
                        findNextMinimumDistanceDailyPick(
                                routeContexts.get(violation.visitDate()),
                                selected,
                                previouslyRecommendedPlaceIds,
                                generatedOptionPlaces,
                                dailyOverlapLimit,
                                triedDailyPickSignatures
                        );
                if (nextPick == null) {
                    log.warn(
                            "도보 20분 초과 구간을 줄일 대체 장소·차선 코스가 없습니다: "
                                    + "resultId={}, visitDate={}, fromPlaceId={}, "
                                    + "toPlaceId={}, actualMinutes={}",
                            resultId,
                            violation.visitDate(),
                            violation.fromPlaceId(),
                            violation.toPlaceId(),
                            round(violation.travelMinutes(), 1)
                    );
                    exhaustedViolationKeys.add(
                            walkingViolationKey(violation)
                    );
                    continue;
                }
                replaceDailySelection(
                        selected,
                        violation.visitDate(),
                        nextPick
                );
                preferredFirstPlaceIds.put(
                        violation.visitDate(),
                        nextPick.firstPlaceId()
                );
                alternateDailyPicksByDate.merge(
                        violation.visitDate(),
                        1,
                        Integer::sum
                );
            } else {
                exhaustedViolationKeys.add(
                        walkingViolationKey(violation)
                );
                continue;
            }

            optimized = optimizeWalkingWithActualRoutes(
                    selected,
                    preferredFirstPlaceIds
            );
            actualOptimizationPasses++;
            quality = walkingRouteQuality(optimized);
            if (compareWalkingRouteQuality(
                    quality,
                    bestQuality
            ) < 0) {
                best = optimized;
                bestQuality = quality;
            }
        }
        int alternateDailyPicks =
                totalValues(alternateDailyPicksByDate);

        int removedPlaces = 0;
        if (findWalkingViolation(best, Set.of()) != null) {
            WalkingHardLimitResult hardLimitResult =
                    removeWalkingViolations(
                            best,
                            strategy,
                            resultId
                    );
            best = hardLimitResult.optimized();
            bestQuality = hardLimitResult.quality();
            removedPlaces = hardLimitResult.removedPlaces();
        }

        WalkingViolation remainingViolation =
                findWalkingViolation(best, Set.of());
        if (remainingViolation != null
                || bestQuality.estimatedLegCount() > 0) {
            throw new IllegalStateException(
                    "도보 20분 이내의 안전한 추천 코스를 만들 수 없습니다. "
                            + "resultId=" + resultId
                            + ", strategy=" + strategy
            );
        }

        log.info(
                "도보 코스 실제 행렬 검증 완료: resultId={}, strategy={}, "
                        + "actualOptimizationPasses={}, replacements={}, "
                        + "alternateDailyPicks={}, removedPlaces={}, "
                        + "remainingViolations={}, "
                        + "maximumMinutes={}, thresholdMinutes={}",
                resultId,
                strategy,
                actualOptimizationPasses,
                replacements,
                alternateDailyPicks,
                removedPlaces,
                bestQuality.violationCount(),
                round(bestQuality.maximumTravelMinutes(), 1),
                WALKING_MAX_MINUTES
        );
        return best;
    }

    /**
     * 대체 후보로 해결되지 않은 장거리 구간은 원인 장소를 제외해 최종 응답에서 제거한다.
     */
    private WalkingHardLimitResult removeWalkingViolations(
            CourseOptimizeResponse source,
            OptionStrategy strategy,
            Long resultId
    ) {
        List<PlaceCandidateDto> retainedCandidates =
                source.getOptimizedPlaces().stream()
                        .map(this::toRouteCandidate)
                        .collect(java.util.stream.Collectors.toCollection(
                                ArrayList::new
                        ));
        Map<LocalDate, Long> preferredFirstPlaceIds =
                source.getOptimizedPlaces().stream()
                        .filter(place -> valueOrZero(
                                place.getVisitOrder()
                        ) == 1)
                        .collect(java.util.stream.Collectors.toMap(
                                OptimizedPlaceDto::getVisitDate,
                                OptimizedPlaceDto::getPlaceId,
                                (left, right) -> left,
                                TreeMap::new
                        ));

        CourseOptimizeResponse current = source;
        WalkingRouteQuality quality = walkingRouteQuality(current);
        int removedPlaces = 0;
        int removalBudget = retainedCandidates.size();

        while (removedPlaces < removalBudget) {
            WalkingViolation violation =
                    findWalkingViolation(current, Set.of());
            if (violation == null) {
                break;
            }

            WalkingRemovalChoice removal = chooseWalkingRemoval(
                    violation,
                    retainedCandidates,
                    preferredFirstPlaceIds
            );
            if (removal == null) {
                throw new IllegalStateException(
                        "도보 20분 초과 장소를 제외할 수 없습니다. "
                                + "resultId=" + resultId
                                + ", visitDate=" + violation.visitDate()
                );
            }

            double previousMaximum = quality.maximumTravelMinutes();
            retainedCandidates = removal.retainedCandidates();
            preferredFirstPlaceIds = removal.preferredFirstPlaceIds();
            current = removal.optimized();
            quality = removal.quality();
            removedPlaces++;

            log.warn(
                    "도보 20분 초과 장소를 코스에서 제외했습니다: "
                            + "resultId={}, strategy={}, visitDate={}, "
                            + "removedPlaceId={}, remainingDayPlaces={}, "
                            + "previousMaximumMinutes={}, newMaximumMinutes={}",
                    resultId,
                    strategy,
                    violation.visitDate(),
                    removal.removedPlaceId(),
                    removal.remainingDayPlaceCount(),
                    round(previousMaximum, 1),
                    round(quality.maximumTravelMinutes(), 1)
            );
        }

        return new WalkingHardLimitResult(
                current,
                quality,
                removedPlaces
        );
    }

    /** 긴 구간 양 끝 중 한 곳을 각각 제외해 더 나은 실제 경로를 선택한다. */
    private WalkingRemovalChoice chooseWalkingRemoval(
            WalkingViolation violation,
            List<PlaceCandidateDto> retainedCandidates,
            Map<LocalDate, Long> preferredFirstPlaceIds
    ) {
        Set<Long> removablePlaceIds = new LinkedHashSet<>();
        removablePlaceIds.add(violation.fromPlaceId());
        removablePlaceIds.add(violation.toPlaceId());

        WalkingRemovalChoice best = null;
        for (Long removedPlaceId : removablePlaceIds) {
            WalkingRemovalChoice candidate = evaluateWalkingRemoval(
                    violation.visitDate(),
                    removedPlaceId,
                    retainedCandidates,
                    preferredFirstPlaceIds
            );
            if (candidate != null
                    && (best == null
                    || compareWalkingRemovalChoices(
                    candidate,
                    best
            ) < 0)) {
                best = candidate;
            }
        }
        return best;
    }

    private WalkingRemovalChoice evaluateWalkingRemoval(
            LocalDate visitDate,
            Long removedPlaceId,
            List<PlaceCandidateDto> retainedCandidates,
            Map<LocalDate, Long> preferredFirstPlaceIds
    ) {
        long dailyPlaceCount = retainedCandidates.stream()
                .filter(candidate -> candidate.getVisitDate().equals(
                        visitDate
                ))
                .count();
        if (dailyPlaceCount <= 1) {
            return null;
        }

        PlaceCandidateDto removed = retainedCandidates.stream()
                .filter(candidate -> candidate.getVisitDate().equals(
                        visitDate
                ))
                .filter(candidate -> candidate.getPlaceId().equals(
                        removedPlaceId
                ))
                .findFirst()
                .orElse(null);
        if (removed == null) {
            return null;
        }

        List<PlaceCandidateDto> remaining =
                retainedCandidates.stream()
                        .filter(candidate ->
                                !candidate.getVisitDate().equals(visitDate)
                                        || !candidate.getPlaceId().equals(
                                        removedPlaceId
                                ))
                        .collect(java.util.stream.Collectors.toCollection(
                                ArrayList::new
                        ));
        Map<LocalDate, Long> nextPreferredFirstPlaceIds =
                new TreeMap<>(preferredFirstPlaceIds);
        if (removedPlaceId.equals(
                nextPreferredFirstPlaceIds.get(visitDate)
        )) {
            nextPreferredFirstPlaceIds.remove(visitDate);
        }

        CourseOptimizeResponse optimized =
                optimizeWalkingWithActualRoutes(
                        remaining,
                        nextPreferredFirstPlaceIds
                );
        WalkingRouteQuality quality = walkingRouteQuality(optimized);
        return new WalkingRemovalChoice(
                List.copyOf(remaining),
                Map.copyOf(nextPreferredFirstPlaceIds),
                optimized,
                quality,
                removedPlaceId,
                valueOrZero(removed.getRecommendationScore()),
                (int) dailyPlaceCount - 1
        );
    }

    private int compareWalkingRemovalChoices(
            WalkingRemovalChoice left,
            WalkingRemovalChoice right
    ) {
        int comparison = compareWalkingRouteQuality(
                left.quality(),
                right.quality()
        );
        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(
                left.removedRecommendationScore(),
                right.removedRecommendationScore()
        );
        return comparison != 0
                ? comparison
                : left.removedPlaceId().compareTo(
                right.removedPlaceId()
        );
    }

    private CourseOptimizeResponse optimizeWalkingWithActualRoutes(
            List<PlaceCandidateDto> selected,
            Map<LocalDate, Long> preferredFirstPlaceIds
    ) {
        // 최대 5일의 선택 장소를 한 ORS Matrix 요청으로 먼저 채운다.
        // 외부 API가 실패해 추정 행렬이 오면 날짜마다 같은 실패를 반복하지 않는다.
        RouteMatrix actualMatrix = distanceService.calculateRouteMatrix(
                selected,
                TransportMode.WALKING
        );
        if (actualMatrix.estimatedTravelTimes()) {
            return courseOptimizationService.optimizeForRecommendation(
                    CourseOptimizeRequest.builder()
                            .transportMode(TransportMode.WALKING)
                            .placeCandidates(selected)
                            .build(),
                    preferredFirstPlaceIds
            );
        }
        return courseOptimizationService.optimize(
                CourseOptimizeRequest.builder()
                        .transportMode(TransportMode.WALKING)
                        .placeCandidates(selected)
                        .build(),
                preferredFirstPlaceIds
        );
    }

    private WalkingViolation findWalkingViolation(
            CourseOptimizeResponse optimized,
            Set<String> ignoredViolationKeys
    ) {
        List<OptimizedPlaceDto> places =
                new ArrayList<>(optimized.getOptimizedPlaces());
        places.sort(Comparator
                .comparing(OptimizedPlaceDto::getVisitDate)
                .thenComparing(OptimizedPlaceDto::getVisitOrder));

        for (int index = 1; index < places.size(); index++) {
            OptimizedPlaceDto previous = places.get(index - 1);
            OptimizedPlaceDto current = places.get(index);
            if (!current.getVisitDate().equals(previous.getVisitDate())) {
                continue;
            }
            double travelMinutes = valueOrZero(
                    current.getTravelTimeFromPreviousMinutes()
            );
            boolean estimated = Boolean.TRUE.equals(
                    current.getRouteEstimated()
            );
            if (!estimated
                    && travelMinutes <= WALKING_MAX_MINUTES) {
                continue;
            }

            Long previousPlaceId = null;
            double previousMinutes = 0.0;
            if (index >= 2) {
                OptimizedPlaceDto beforePrevious =
                        places.get(index - 2);
                if (beforePrevious.getVisitDate().equals(
                        previous.getVisitDate()
                )) {
                    previousPlaceId = beforePrevious.getPlaceId();
                    previousMinutes = valueOrZero(
                            previous.getTravelTimeFromPreviousMinutes()
                    );
                }
            }
            Long nextPlaceId = null;
            double nextMinutes = 0.0;
            if (index + 1 < places.size()) {
                OptimizedPlaceDto next = places.get(index + 1);
                if (next.getVisitDate().equals(current.getVisitDate())) {
                    nextPlaceId = next.getPlaceId();
                    nextMinutes = valueOrZero(
                            next.getTravelTimeFromPreviousMinutes()
                    );
                }
            }
            WalkingViolation violation = new WalkingViolation(
                    current.getVisitDate(),
                    previousPlaceId,
                    previous.getPlaceId(),
                    current.getPlaceId(),
                    nextPlaceId,
                    previousMinutes,
                    travelMinutes,
                    nextMinutes,
                    estimated
            );
            if (ignoredViolationKeys == null
                    || !ignoredViolationKeys.contains(
                    walkingViolationKey(violation)
            )) {
                return violation;
            }
        }
        return null;
    }

    private String walkingViolationKey(
            WalkingViolation violation
    ) {
        return violation.visitDate()
                + "|" + violation.fromPlaceId()
                + ">" + violation.toPlaceId();
    }

    private WalkingReplacement findWalkingReplacement(
            WalkingViolation violation,
            List<PlaceCandidateDto> selected,
            DailyRouteContext context,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        if (context == null) {
            return null;
        }
        List<WalkingReplacementTarget> targets = List.of(
                new WalkingReplacementTarget(
                        violation.toPlaceId(),
                        violation.fromPlaceId(),
                        violation.nextPlaceId(),
                        Math.max(
                                violation.travelMinutes(),
                                violation.nextMinutes()
                        )
                ),
                new WalkingReplacementTarget(
                        violation.fromPlaceId(),
                        violation.previousPlaceId(),
                        violation.toPlaceId(),
                        Math.max(
                                violation.previousMinutes(),
                                violation.travelMinutes()
                        )
                )
        );
        WalkingReplacement best = null;
        for (WalkingReplacementTarget target : targets) {
            WalkingReplacement candidate =
                    findWalkingReplacementForTarget(
                            target,
                            violation.visitDate(),
                            selected,
                            context,
                            previouslyRecommendedPlaceIds,
                            generatedOptionPlaces,
                            dailyOverlapLimit
                    );
            if (candidate != null
                    && (best == null
                    || compareWalkingReplacements(
                    candidate,
                    best
            ) < 0)) {
                best = candidate;
            }
        }
        return best;
    }

    private WalkingReplacement findWalkingReplacementForTarget(
            WalkingReplacementTarget target,
            LocalDate visitDate,
            List<PlaceCandidateDto> selected,
            DailyRouteContext context,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        PlaceCandidateDto replaced = findSelectedCandidate(
                selected,
                visitDate,
                target.replacedPlaceId()
        );
        PlaceCandidateDto previous =
                target.previousPlaceId() == null
                        ? null
                        : findSelectedCandidate(
                        selected,
                        visitDate,
                        target.previousPlaceId()
                );
        PlaceCandidateDto next =
                target.nextPlaceId() == null
                        ? null
                        : findSelectedCandidate(
                        selected,
                        visitDate,
                        target.nextPlaceId()
                );
        List<PlaceCandidateDto> dailyWithoutReplaced =
                selected.stream()
                        .filter(candidate -> candidate.getVisitDate().equals(
                                visitDate
                        ))
                        .filter(candidate -> !candidate.getPlaceId().equals(
                                target.replacedPlaceId()
                        ))
                        .toList();
        Set<Long> selectedIds = selected.stream()
                .filter(candidate -> candidate.getVisitDate().equals(
                        visitDate
                ))
                .map(PlaceCandidateDto::getPlaceId)
                .collect(java.util.stream.Collectors.toSet());

        List<PlaceCandidateDto> candidates =
                context.plan().placeCandidates().stream()
                        .filter(candidate -> candidate.getCategory().equals(
                                replaced.getCategory()
                        ))
                        .filter(candidate -> !selectedIds.contains(
                                candidate.getPlaceId()
                        ))
                        .sorted((left, right) ->
                                compareWalkingReplacementCandidates(
                                        left,
                                        right,
                                        previous,
                                        next,
                                        dailyWithoutReplaced,
                                        context,
                                        previouslyRecommendedPlaceIds,
                                        generatedOptionPlaces,
                                        dailyOverlapLimit
                                ))
                        .limit(MAX_WALKING_REPLACEMENT_CANDIDATES)
                        .map(candidate -> copyCandidateWithDate(
                                candidate,
                                visitDate,
                                false
                        ))
                        .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        List<PlaceCandidateDto> matrixCandidates = new ArrayList<>();
        int previousIndex = -1;
        if (previous != null) {
            previousIndex = matrixCandidates.size();
            matrixCandidates.add(previous);
        }
        int nextIndex = -1;
        if (next != null) {
            nextIndex = matrixCandidates.size();
            matrixCandidates.add(next);
        }
        int firstCandidateIndex = matrixCandidates.size();
        matrixCandidates.addAll(candidates);
        RouteMatrix matrix = distanceService.calculateRouteMatrix(
                matrixCandidates,
                TransportMode.WALKING
        );

        WalkingReplacementEvaluation best = null;
        for (int offset = 0; offset < candidates.size(); offset++) {
            int candidateIndex = firstCandidateIndex + offset;
            double incomingMinutes = 0.0;
            if (previousIndex >= 0) {
                if (matrix.isEstimated(
                        previousIndex,
                        candidateIndex
                )) {
                    continue;
                }
                incomingMinutes =
                        matrix.getTravelTimeMinutes(
                                previousIndex,
                                candidateIndex
                        );
            }
            double outgoingMinutes = 0.0;
            if (nextIndex >= 0) {
                if (matrix.isEstimated(candidateIndex, nextIndex)) {
                    continue;
                }
                outgoingMinutes = matrix.getTravelTimeMinutes(
                        candidateIndex,
                        nextIndex
                );
            }
            WalkingReplacementEvaluation evaluation =
                    new WalkingReplacementEvaluation(
                            candidates.get(offset),
                            Math.max(incomingMinutes, outgoingMinutes),
                            incomingMinutes + outgoingMinutes
                    );
            if (best == null
                    || compareWalkingReplacementEvaluations(
                    evaluation,
                    best
            ) < 0) {
                best = evaluation;
            }
        }
        if (best == null) {
            return null;
        }
        if (best.maximumMinutes() <= WALKING_MAX_MINUTES
                || best.maximumMinutes() + EPSILON
                < target.baselineMaximumMinutes()) {
            return new WalkingReplacement(
                    target.replacedPlaceId(),
                    best.candidate(),
                    best.maximumMinutes(),
                    best.totalMinutes()
            );
        }
        return null;
    }

    private int compareWalkingReplacements(
            WalkingReplacement left,
            WalkingReplacement right
    ) {
        boolean leftWithin =
                left.maximumMinutes() <= WALKING_MAX_MINUTES;
        boolean rightWithin =
                right.maximumMinutes() <= WALKING_MAX_MINUTES;
        int comparison = Boolean.compare(rightWithin, leftWithin);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(
                left.maximumMinutes(),
                right.maximumMinutes()
        );
        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(
                left.totalMinutes(),
                right.totalMinutes()
        );
        return comparison != 0
                ? comparison
                : left.candidate().getPlaceId()
                .compareTo(right.candidate().getPlaceId());
    }

    private int compareWalkingReplacementCandidates(
            PlaceCandidateDto left,
            PlaceCandidateDto right,
            PlaceCandidateDto previous,
            PlaceCandidateDto next,
            List<PlaceCandidateDto> dailyWithoutReplaced,
            DailyRouteContext context,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit
    ) {
        int comparison = Integer.compare(
                candidateRestrictionLevel(
                        left,
                        dailyWithoutReplaced,
                        context.plan().visitDate(),
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit
                ),
                candidateRestrictionLevel(
                        right,
                        dailyWithoutReplaced,
                        context.plan().visitDate(),
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit
                )
        );
        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(
                estimatedReplacementCost(
                        previous,
                        left,
                        next,
                        context
                ),
                estimatedReplacementCost(
                        previous,
                        right,
                        next,
                        context
                )
        );
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
        comparison = Double.compare(
                valueOrZero(right.getRecommendationScore()),
                valueOrZero(left.getRecommendationScore())
        );
        return comparison != 0
                ? comparison
                : left.getPlaceId().compareTo(right.getPlaceId());
    }

    private int compareWalkingReplacementEvaluations(
            WalkingReplacementEvaluation left,
            WalkingReplacementEvaluation right
    ) {
        boolean leftWithin =
                left.maximumMinutes() <= WALKING_MAX_MINUTES;
        boolean rightWithin =
                right.maximumMinutes() <= WALKING_MAX_MINUTES;
        int comparison = Boolean.compare(rightWithin, leftWithin);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(
                left.maximumMinutes(),
                right.maximumMinutes()
        );
        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(
                left.totalMinutes(),
                right.totalMinutes()
        );
        return comparison != 0
                ? comparison
                : left.candidate().getPlaceId()
                .compareTo(right.candidate().getPlaceId());
    }

    private double estimatedReplacementCost(
            PlaceCandidateDto previous,
            PlaceCandidateDto candidate,
            PlaceCandidateDto next,
            DailyRouteContext context
    ) {
        Integer candidateIndex =
                context.candidateIndexes().get(candidate.getPlaceId());
        if (candidateIndex == null) {
            return Double.POSITIVE_INFINITY;
        }
        double cost = 0.0;
        if (previous != null) {
            Integer previousIndex =
                    context.candidateIndexes().get(
                            previous.getPlaceId()
                    );
            if (previousIndex == null) {
                return Double.POSITIVE_INFINITY;
            }
            cost += context.routeMatrix().getTravelTimeMinutes(
                    previousIndex,
                    candidateIndex
            );
        }
        if (next != null) {
            Integer nextIndex =
                    context.candidateIndexes().get(next.getPlaceId());
            if (nextIndex != null) {
                cost += context.routeMatrix().getTravelTimeMinutes(
                        candidateIndex,
                        nextIndex
                );
            }
        }
        return cost;
    }

    private int totalValues(Map<LocalDate, Integer> values) {
        return values.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private DailyPick findNextMinimumDistanceDailyPick(
            DailyRouteContext context,
            List<PlaceCandidateDto> selected,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            Set<String> triedDailyPickSignatures
    ) {
        if (context == null) {
            return null;
        }
        List<DailyPick> ranked = context.plan().placeCandidates().stream()
                .filter(candidate -> context.plan().categoryTargets()
                        .getOrDefault(candidate.getCategory(), 0) > 0)
                .map(firstCandidate -> buildGreedyDailyPick(
                        context,
                        OptionStrategy.MIN_DISTANCE,
                        firstCandidate,
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit
                ))
                .sorted((left, right) -> compareDailyPicks(
                        left,
                        right,
                        OptionStrategy.MIN_DISTANCE,
                        context
                ))
                .toList();

        for (DailyPick pick : ranked) {
            String key = context.plan().visitDate() + "|"
                    + pick.signature();
            if (triedDailyPickSignatures.add(key)) {
                return pick;
            }
        }
        return null;
    }

    private void replaceDailySelection(
            List<PlaceCandidateDto> selected,
            LocalDate visitDate,
            DailyPick pick
    ) {
        selected.removeIf(candidate -> candidate.getVisitDate().equals(
                visitDate
        ));
        for (PlaceCandidateDto candidate : pick.placeCandidates()) {
            selected.add(copyCandidateWithDate(
                    candidate,
                    visitDate,
                    false
            ));
        }
    }

    private String dailySelectionSignature(
            List<PlaceCandidateDto> selected,
            LocalDate visitDate
    ) {
        return selected.stream()
                .filter(candidate -> candidate.getVisitDate().equals(
                        visitDate
                ))
                .map(PlaceCandidateDto::getPlaceId)
                .sorted()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    /**
     * 최종 도보 DAY마다 실제 경로·구간 20분·평균 18분 상한을 다시 검증한다.
     * 장소 선발, 자연스러운 순서 보정, 숙소 추가 중 어느 단계에서도 제한이 우회되지 않게 한다.
     */
    private void validateWalkingDailyTimeLimits(
            CourseOptimizeResponse optimized,
            Long resultId,
            OptionStrategy strategy,
            String stage
    ) {
        if (optimized == null
                || optimized.getOptimizedPlaces() == null) {
            throw new IllegalStateException(
                    "도보 코스 검증 대상이 없습니다. resultId=" + resultId
            );
        }

        Map<LocalDate, List<OptimizedPlaceDto>> placesByDate =
                new TreeMap<>();
        for (OptimizedPlaceDto place : optimized.getOptimizedPlaces()) {
            placesByDate
                    .computeIfAbsent(
                            place.getVisitDate(),
                            ignored -> new ArrayList<>()
                    )
                    .add(place);
        }

        for (Map.Entry<LocalDate, List<OptimizedPlaceDto>> entry
                : placesByDate.entrySet()) {
            List<OptimizedPlaceDto> dailyPlaces = entry.getValue().stream()
                    .sorted(Comparator.comparing(
                            OptimizedPlaceDto::getVisitOrder
                    ))
                    .toList();
            int legCount = 0;
            int estimatedLegCount = 0;
            double totalMinutes = 0.0;
            double maximumMinutes = 0.0;

            for (OptimizedPlaceDto place : dailyPlaces) {
                if (valueOrZero(place.getVisitOrder()) <= 1) {
                    continue;
                }
                legCount++;
                double minutes = valueOrZero(
                        place.getTravelTimeFromPreviousMinutes()
                );
                totalMinutes += minutes;
                maximumMinutes = Math.max(maximumMinutes, minutes);
                if (Boolean.TRUE.equals(place.getRouteEstimated())) {
                    estimatedLegCount++;
                }
            }

            double maximumTotalMinutes = legCount
                    * WALKING_RELAXED_AVERAGE_MINUTES;
            if (estimatedLegCount > 0
                    || maximumMinutes > WALKING_MAX_MINUTES + EPSILON
                    || totalMinutes > maximumTotalMinutes + EPSILON) {
                throw new IllegalStateException(
                        "도보 제한을 벗어난 DAY는 반환할 수 없습니다. "
                                + "resultId=" + resultId
                                + ", strategy=" + strategy
                                + ", stage=" + stage
                                + ", visitDate=" + entry.getKey()
                                + ", places=" + dailyPlaces.size()
                                + ", legs=" + legCount
                                + ", totalMinutes=" + round(totalMinutes, 1)
                                + ", averageMinutes="
                                + round(
                                legCount == 0
                                        ? 0.0
                                        : totalMinutes / legCount,
                                1
                        )
                                + ", maximumMinutes="
                                + round(maximumMinutes, 1)
                                + ", estimatedLegs=" + estimatedLegCount
                );
            }

            log.info(
                    "도보 DAY 제한 검증 완료: resultId={}, strategy={}, "
                            + "stage={}, visitDate={}, places={}, totalMinutes={}, "
                            + "averageMinutes={}, maximumMinutes={}",
                    resultId,
                    strategy,
                    stage,
                    entry.getKey(),
                    dailyPlaces.size(),
                    round(totalMinutes, 1),
                    round(
                            legCount == 0
                                    ? 0.0
                                    : totalMinutes / legCount,
                            1
                    ),
                    round(maximumMinutes, 1)
            );
        }
    }

    private WalkingRouteQuality walkingRouteQuality(
            CourseOptimizeResponse optimized
    ) {
        int violationCount = 0;
        int estimatedLegCount = 0;
        int actualLegCount = 0;
        double maximumTravelMinutes = 0.0;
        double totalTravelMinutes = 0.0;

        for (OptimizedPlaceDto place : optimized.getOptimizedPlaces()) {
            if (valueOrZero(place.getVisitOrder()) <= 1) {
                continue;
            }
            double minutes = valueOrZero(
                    place.getTravelTimeFromPreviousMinutes()
            );
            maximumTravelMinutes = Math.max(
                    maximumTravelMinutes,
                    minutes
            );
            totalTravelMinutes += minutes;
            boolean estimated = Boolean.TRUE.equals(
                    place.getRouteEstimated()
            );
            if (estimated || minutes > WALKING_MAX_MINUTES) {
                violationCount++;
            }
            if (estimated) {
                estimatedLegCount++;
                continue;
            }
            actualLegCount++;
        }
        return new WalkingRouteQuality(
                violationCount,
                estimatedLegCount,
                actualLegCount,
                maximumTravelMinutes,
                totalTravelMinutes
        );
    }

    private int compareWalkingRouteQuality(
            WalkingRouteQuality left,
            WalkingRouteQuality right
    ) {
        int comparison = Integer.compare(
                left.violationCount(),
                right.violationCount()
        );
        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(
                left.maximumTravelMinutes(),
                right.maximumTravelMinutes()
        );
        if (comparison != 0) {
            return comparison;
        }
        comparison = Double.compare(
                left.totalTravelMinutes(),
                right.totalTravelMinutes()
        );
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compare(
                left.estimatedLegCount(),
                right.estimatedLegCount()
        );
    }

    private PlaceCandidateDto findSelectedCandidate(
            List<PlaceCandidateDto> selected,
            LocalDate visitDate,
            Long placeId
    ) {
        return selected.stream()
                .filter(candidate -> candidate.getVisitDate().equals(visitDate))
                .filter(candidate -> candidate.getPlaceId().equals(placeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "선택 코스에서 장소를 찾을 수 없습니다. placeId=" + placeId
                ));
    }

    private void replaceSelectedCandidate(
            List<PlaceCandidateDto> selected,
            LocalDate visitDate,
            Long replacedPlaceId,
            PlaceCandidateDto replacement
    ) {
        for (int index = 0; index < selected.size(); index++) {
            PlaceCandidateDto current = selected.get(index);
            if (current.getVisitDate().equals(visitDate)
                    && current.getPlaceId().equals(replacedPlaceId)) {
                selected.set(index, replacement);
                return;
            }
        }
        throw new IllegalArgumentException(
                "교체할 장소를 선택 코스에서 찾을 수 없습니다. placeId="
                        + replacedPlaceId
        );
    }

    /** 코스별 마지막 일반 장소와 가까운 숙소를 전략에 맞춰 독립적으로 선택한다. */
    private HotelEvaluation selectHotelCandidateForOption(
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
        List<HotelEvaluation> evaluations = createHotelEvaluations(
                hotelCandidates,
                lastPlaces,
                optimized.getTransportMode()
        );
        if (optimized.getTransportMode() == TransportMode.WALKING) {
            List<HotelEvaluation> strictHotels = evaluations.stream()
                    .filter(evaluation -> !evaluation.estimated())
                    .filter(evaluation ->
                            evaluation.maximumTravelMinutes()
                                    <= WALKING_TARGET_AVERAGE_MINUTES
                                    + EPSILON)
                    .toList();
            List<HotelEvaluation> relaxedHotels = evaluations.stream()
                    .filter(evaluation -> !evaluation.estimated())
                    .filter(evaluation ->
                            evaluation.maximumTravelMinutes()
                                    <= WALKING_RELAXED_AVERAGE_MINUTES
                                    + EPSILON)
                    .toList();

            if (!strictHotels.isEmpty()) {
                evaluations = strictHotels;
            } else if (!relaxedHotels.isEmpty()) {
                evaluations = relaxedHotels;
            } else {
                double closestMinutes = evaluations.stream()
                        .filter(evaluation -> !evaluation.estimated())
                        .mapToDouble(
                                HotelEvaluation::maximumTravelMinutes
                        )
                        .min()
                        .orElse(Double.NaN);
                log.warn(
                        "도보 평균 18분 제한을 지킬 숙소가 없어 코스에 적용하지 않습니다: "
                                + "closestMaximumMinutes={}",
                        Double.isFinite(closestMinutes)
                                ? round(closestMinutes, 1)
                                : "unavailable"
                );
                return null;
            }
        }
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
                .findFirst()
                .orElse(null);
    }

    private List<HotelEvaluation> createHotelEvaluations(
            List<PlaceCandidateDto> hotelCandidates,
            List<OptimizedPlaceDto> lastPlaces,
            TransportMode transportMode
    ) {
        Map<Long, Double> averageDistances = new LinkedHashMap<>();
        for (PlaceCandidateDto hotel : hotelCandidates) {
            averageDistances.put(
                    hotel.getPlaceId(),
                    averageDirectDistanceKm(lastPlaces, hotel)
            );
        }
        if (transportMode != TransportMode.WALKING
                || lastPlaces.isEmpty()) {
            return hotelCandidates.stream()
                    .map(hotel -> new HotelEvaluation(
                            hotel,
                            averageDistances.get(hotel.getPlaceId()),
                            Double.POSITIVE_INFINITY,
                            Double.POSITIVE_INFINITY,
                            true
                    ))
                    .toList();
        }

        List<PlaceCandidateDto> matrixCandidates = new ArrayList<>();
        for (OptimizedPlaceDto lastPlace : lastPlaces) {
            matrixCandidates.add(toRouteCandidate(lastPlace));
        }
        int firstHotelIndex = matrixCandidates.size();
        matrixCandidates.addAll(hotelCandidates);
        RouteMatrix matrix = distanceService.calculateRouteMatrix(
                matrixCandidates,
                TransportMode.WALKING
        );

        List<HotelEvaluation> evaluations = new ArrayList<>();
        for (int hotelOffset = 0;
             hotelOffset < hotelCandidates.size();
             hotelOffset++) {
            int hotelIndex = firstHotelIndex + hotelOffset;
            double maximumMinutes = 0.0;
            double totalMinutes = 0.0;
            boolean estimated = false;
            for (int lastIndex = 0;
                 lastIndex < lastPlaces.size();
                 lastIndex++) {
                double minutes = matrix.getTravelTimeMinutes(
                        lastIndex,
                        hotelIndex
                );
                maximumMinutes = Math.max(maximumMinutes, minutes);
                totalMinutes += minutes;
                estimated |= matrix.isEstimated(lastIndex, hotelIndex);
            }
            PlaceCandidateDto hotel = hotelCandidates.get(hotelOffset);
            evaluations.add(new HotelEvaluation(
                    hotel,
                    averageDistances.get(hotel.getPlaceId()),
                    maximumMinutes,
                    totalMinutes / lastPlaces.size(),
                    estimated
            ));
        }
        return List.copyOf(evaluations);
    }

    private PlaceCandidateDto toRouteCandidate(
            OptimizedPlaceDto place
    ) {
        return PlaceCandidateDto.builder()
                .placeId(place.getPlaceId())
                .placeName(place.getPlaceName())
                .category(place.getCategory())
                .address(place.getAddress())
                .roadAddress(place.getRoadAddress())
                .imageUrl(place.getImageUrl())
                .recommendationScore(place.getRecommendationScore())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .visitDate(place.getVisitDate())
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
                .alternativeCandidates(List.of())
                .build();
    }

    /**
     * 숙소 평가에서 이미 조회·캐시한 실제 마지막 장소→숙소 값을 최종 응답에도 반영한다.
     */
    private CourseOptimizeResponse applyActualWalkingHotelLegs(
            CourseOptimizeResponse optimized,
            PlaceCandidateDto selectedHotel
    ) {
        List<OptimizedPlaceDto> places =
                new ArrayList<>(optimized.getOptimizedPlaces());
        places.sort(Comparator
                .comparing(OptimizedPlaceDto::getVisitDate)
                .thenComparing(OptimizedPlaceDto::getVisitOrder));

        for (int index = 1; index < places.size(); index++) {
            OptimizedPlaceDto hotel = places.get(index);
            OptimizedPlaceDto previous = places.get(index - 1);
            if (!"HOTEL".equalsIgnoreCase(hotel.getCategory())
                    || !hotel.getPlaceId().equals(
                    selectedHotel.getPlaceId()
            )
                    || !hotel.getVisitDate().equals(
                    previous.getVisitDate()
            )) {
                continue;
            }
            RouteMatrix matrix = distanceService.calculateRouteMatrix(
                    List.of(
                            toRouteCandidate(previous),
                            copyCandidateWithDate(
                                    selectedHotel,
                                    hotel.getVisitDate(),
                                    false
                            )
                    ),
                    TransportMode.WALKING
            );
            hotel.setDistanceFromPreviousKm(
                    matrix.getDistanceKm(0, 1)
            );
            hotel.setTravelTimeFromPreviousMinutes(
                    matrix.getTravelTimeMinutes(0, 1)
            );
            hotel.setTransitPathType(
                    matrix.getTransitPathType(0, 1)
            );
            hotel.setRouteEstimated(matrix.isEstimated(0, 1));
        }
        return recalculateOptimizeTotals(optimized);
    }

    private CourseOptimizeResponse recalculateOptimizeTotals(
            CourseOptimizeResponse optimized
    ) {
        double distanceKm = optimized.getOptimizedPlaces().stream()
                .mapToDouble(place -> valueOrZero(
                        place.getDistanceFromPreviousKm()
                ))
                .sum();
        double travelMinutes = optimized.getOptimizedPlaces().stream()
                .mapToDouble(place -> valueOrZero(
                        place.getTravelTimeFromPreviousMinutes()
                ))
                .sum();
        int visitMinutes = optimized.getOptimizedPlaces().stream()
                .mapToInt(place -> valueOrZero(
                        place.getExpectedVisitMinutes()
                ))
                .sum();
        optimized.setTotalDistanceKm(distanceKm);
        optimized.setTotalTravelTimeMinutes(travelMinutes);
        optimized.setTotalVisitTimeMinutes(visitMinutes);
        optimized.setTotalCourseTimeMinutes(
                visitMinutes + travelMinutes
        );
        optimized.setEstimatedTravelTimes(
                optimized.getOptimizedPlaces().stream()
                        .anyMatch(place -> Boolean.TRUE.equals(
                                place.getRouteEstimated()
                        ))
        );
        return optimized;
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
        // 다른 선택지가 있으면 앞 옵션 또는 직전 추천에서 사용하지 않은 숙소를 먼저 고른다.
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
                    left.estimated() ? 1.0 : 0.0,
                    right.estimated() ? 1.0 : 0.0
            );
            if (comparison == 0) {
                comparison = Double.compare(
                        left.maximumTravelMinutes(),
                        right.maximumTravelMinutes()
                );
            }
            if (comparison == 0) {
                comparison = Double.compare(
                        left.averageTravelMinutes(),
                        right.averageTravelMinutes()
                );
            }
            if (comparison == 0) {
                comparison = Double.compare(
                        left.averageDistanceKm(),
                        right.averageDistanceKm()
                );
            }
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
        if (comparison != 0) {
            return comparison;
        }
        return left.hotel().getPlaceId()
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

    /**
     * 선발된 장소는 그대로 유지하고 방문 순서만 자연스럽게 보정한다.
     *
     * <p>관광지가 있으면 첫 장소는 관광지를 우선한다. 가능한 순서 중 최소 이동시간보다
     * 15분 또는 20%를 초과해 느려지는 경로는 제외한 뒤, 점심시간 식당 배치,
     * 음식점·카페 연속 방지, 오후 카페 배치를 가장 잘 만족하는 순서를 고른다.
     * 도보는 기존의 실제 20분 이내 간선만 사용하므로 하드 제한이 유지된다.</p>
     */
    private CourseOptimizeResponse applyNaturalScheduleFlow(
            CourseOptimizeResponse optimized,
            LocalTime dailyStartTime,
            Map<LocalDate, DailyRouteContext> routeContexts,
            Map<LocalDate, Set<Long>> usedFirstPlaceIdsByDate
    ) {
        if (optimized == null
                || optimized.getOptimizedPlaces() == null
                || optimized.getOptimizedPlaces().isEmpty()
                || dailyStartTime == null) {
            return optimized;
        }

        Map<LocalDate, List<OptimizedPlaceDto>> placesByDate = new TreeMap<>();
        for (OptimizedPlaceDto place : optimized.getOptimizedPlaces()) {
            placesByDate
                    .computeIfAbsent(place.getVisitDate(), ignored -> new ArrayList<>())
                    .add(place);
        }

        List<OptimizedPlaceDto> reordered = new ArrayList<>();
        boolean estimatedTravelTimes = false;
        double totalDistanceKm = 0.0;
        double totalTravelTimeMinutes = 0.0;
        int totalVisitTimeMinutes = 0;

        for (Map.Entry<LocalDate, List<OptimizedPlaceDto>> entry
                : placesByDate.entrySet()) {
            List<OptimizedPlaceDto> dailyPlaces = entry.getValue().stream()
                    .sorted(Comparator.comparing(OptimizedPlaceDto::getVisitOrder))
                    .toList();

            NaturalRouteContext naturalContext = createNaturalRouteContext(
                    dailyPlaces,
                    optimized.getTransportMode(),
                    routeContexts.get(entry.getKey())
            );
            if (naturalContext == null
                    || dailyPlaces.size() > MAX_NATURAL_ROUTE_PLACES) {
                for (OptimizedPlaceDto place : dailyPlaces) {
                    reordered.add(place);
                    totalDistanceKm += valueOrZero(
                            place.getDistanceFromPreviousKm()
                    );
                    totalTravelTimeMinutes += valueOrZero(
                            place.getTravelTimeFromPreviousMinutes()
                    );
                    totalVisitTimeMinutes += valueOrZero(
                            place.getExpectedVisitMinutes()
                    );
                    estimatedTravelTimes |= Boolean.TRUE.equals(
                            place.getRouteEstimated()
                    );
                }
                continue;
            }

            List<Integer> route = chooseNaturalRoute(
                    dailyPlaces,
                    naturalContext,
                    dailyStartTime,
                    optimized.getTransportMode() == TransportMode.WALKING,
                    usedFirstPlaceIdsByDate == null
                            ? Set.of()
                            : usedFirstPlaceIdsByDate.getOrDefault(
                                    entry.getKey(),
                                    Set.of()
                            )
            );

            for (int routePosition = 0;
                 routePosition < route.size();
                 routePosition++) {
                int currentPosition = route.get(routePosition);
                OptimizedPlaceDto source = dailyPlaces.get(currentPosition);
                double distanceKm = 0.0;
                double travelMinutes = 0.0;
                TransitPathType pathType = null;
                boolean estimated = false;

                if (routePosition > 0) {
                    int previousPosition = route.get(routePosition - 1);
                    int previousMatrixIndex = naturalContext.matrixIndexes()
                            .get(previousPosition);
                    int currentMatrixIndex = naturalContext.matrixIndexes()
                            .get(currentPosition);
                    distanceKm = naturalContext.routeMatrix().getDistanceKm(
                            previousMatrixIndex,
                            currentMatrixIndex
                    );
                    travelMinutes = naturalContext.routeMatrix()
                            .getTravelTimeMinutes(
                                    previousMatrixIndex,
                                    currentMatrixIndex
                            );
                    pathType = naturalContext.routeMatrix().getTransitPathType(
                            previousMatrixIndex,
                            currentMatrixIndex
                    );
                    estimated = naturalContext.routeMatrix().isEstimated(
                            previousMatrixIndex,
                            currentMatrixIndex
                    );
                }

                reordered.add(copyOptimizedPlaceWithRoute(
                        source,
                        routePosition + 1,
                        distanceKm,
                        travelMinutes,
                        pathType,
                        estimated
                ));
                totalDistanceKm += distanceKm;
                totalTravelTimeMinutes += travelMinutes;
                totalVisitTimeMinutes += valueOrZero(
                        source.getExpectedVisitMinutes()
                );
                estimatedTravelTimes |= estimated;
            }
        }

        return CourseOptimizeResponse.builder()
                .transportMode(optimized.getTransportMode())
                .estimatedTravelTimes(estimatedTravelTimes)
                .optimizedPlaces(reordered)
                .totalDistanceKm(totalDistanceKm)
                .totalTravelTimeMinutes(totalTravelTimeMinutes)
                .totalVisitTimeMinutes(totalVisitTimeMinutes)
                .totalCourseTimeMinutes(
                        totalVisitTimeMinutes + totalTravelTimeMinutes
                )
                .build();
    }

    /** 앞 옵션과 같은 DAY 시작 장소를 피하되, 선택된 장소 구성은 바꾸지 않는다. */
    private int firstPlaceReuseLevel(
            NaturalRouteEvaluation evaluation,
            List<OptimizedPlaceDto> dailyPlaces,
            Set<Long> usedFirstPlaceIds
    ) {
        if (evaluation == null
                || evaluation.route() == null
                || evaluation.route().isEmpty()
                || usedFirstPlaceIds == null
                || usedFirstPlaceIds.isEmpty()) {
            return 0;
        }
        Long firstPlaceId = dailyPlaces
                .get(evaluation.route().get(0))
                .getPlaceId();
        return usedFirstPlaceIds.contains(firstPlaceId) ? 1 : 0;
    }

    /** 생성이 끝난 옵션의 DAY별 첫 일반 장소를 다음 옵션의 시작 장소 회피 기준으로 기록한다. */
    private void rememberFirstPlaceIdsByDate(
            CourseOptimizeResponse optimized,
            Map<LocalDate, Set<Long>> usedFirstPlaceIdsByDate
    ) {
        if (optimized == null
                || optimized.getOptimizedPlaces() == null
                || usedFirstPlaceIdsByDate == null) {
            return;
        }
        for (OptimizedPlaceDto place : optimized.getOptimizedPlaces()) {
            if ("HOTEL".equalsIgnoreCase(place.getCategory())
                    || valueOrZero(place.getVisitOrder()) != 1) {
                continue;
            }
            usedFirstPlaceIdsByDate
                    .computeIfAbsent(
                            place.getVisitDate(),
                            ignored -> new LinkedHashSet<>()
                    )
                    .add(place.getPlaceId());
        }
    }

    /** 선택된 장소만 사용하는 경로 행렬과 인덱스 매핑을 만든다. */
    private NaturalRouteContext createNaturalRouteContext(
            List<OptimizedPlaceDto> dailyPlaces,
            TransportMode transportMode,
            DailyRouteContext originalContext
    ) {
        if (transportMode == TransportMode.WALKING) {
            if (originalContext == null) {
                return null;
            }
            List<Integer> indexes = new ArrayList<>();
            for (OptimizedPlaceDto place : dailyPlaces) {
                Integer index = originalContext.candidateIndexes().get(
                        place.getPlaceId()
                );
                // 대체 후보가 원래 후보 행렬에 없으면 기존 순서를 그대로 유지한다.
                if (index == null) {
                    return null;
                }
                indexes.add(index);
            }
            return new NaturalRouteContext(
                    originalContext.routeMatrix(),
                    List.copyOf(indexes)
            );
        }

        List<PlaceCandidateDto> candidates = dailyPlaces.stream()
                .map(this::toRouteCandidate)
                .toList();
        RouteMatrix routeMatrix = distanceService.calculateCandidatePoolMatrix(
                candidates,
                transportMode
        );
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            indexes.add(index);
        }
        return new NaturalRouteContext(routeMatrix, List.copyOf(indexes));
    }

    /** 최소 동선 허용 범위 안에서 관광지 시작과 식사·카페 흐름이 가장 좋은 순서를 찾는다. */
    private List<Integer> chooseNaturalRoute(
            List<OptimizedPlaceDto> dailyPlaces,
            NaturalRouteContext context,
            LocalTime dailyStartTime,
            boolean walking,
            Set<Long> usedFirstPlaceIds
    ) {
        List<Integer> allStarts = createOrderedIndexes(dailyPlaces.size());
        List<NaturalRouteEvaluation> evaluations = evaluateNaturalRoutes(
                dailyPlaces,
                context,
                dailyStartTime,
                walking,
                allStarts
        );
        if (evaluations.isEmpty()) {
            return createOrderedIndexes(dailyPlaces.size());
        }

        List<NaturalRouteEvaluation> allowedEvaluations;
        if (walking) {
            double strictTotalMinutes = walkingTotalTimeLimit(
                    dailyPlaces.size(),
                    WALKING_TARGET_AVERAGE_MINUTES
            );
            double relaxedTotalMinutes = walkingTotalTimeLimit(
                    dailyPlaces.size(),
                    WALKING_RELAXED_AVERAGE_MINUTES
            );
            allowedEvaluations = evaluations.stream()
                    .filter(evaluation -> evaluation.travelTimeMinutes()
                            <= strictTotalMinutes + EPSILON)
                    .toList();
            if (allowedEvaluations.isEmpty()) {
                allowedEvaluations = evaluations.stream()
                        .filter(evaluation -> evaluation.travelTimeMinutes()
                                <= relaxedTotalMinutes + EPSILON)
                        .toList();
            }
            if (allowedEvaluations.isEmpty()) {
                throw new IllegalStateException(
                        "자연스러운 일정 보정 중 도보 평균 18분 제한을 만족하는 순서를 "
                                + "찾을 수 없습니다."
                );
            }
        } else {
            double minimumTravelMinutes = evaluations.stream()
                    .mapToDouble(NaturalRouteEvaluation::travelTimeMinutes)
                    .min()
                    .orElse(0.0);
            double maximumAllowedMinutes = minimumTravelMinutes + Math.max(
                    MAX_NATURAL_ROUTE_EXTRA_MINUTES,
                    minimumTravelMinutes * MAX_NATURAL_ROUTE_EXTRA_RATIO
            );
            allowedEvaluations = evaluations.stream()
                    .filter(evaluation -> evaluation.travelTimeMinutes()
                            <= maximumAllowedMinutes + EPSILON)
                    .toList();
        }

        return allowedEvaluations.stream()
                .min(Comparator
                        .comparingInt((NaturalRouteEvaluation evaluation) -> firstPlaceReuseLevel(
                                evaluation,
                                dailyPlaces,
                                usedFirstPlaceIds
                        ))
                        .thenComparingDouble(
                                NaturalRouteEvaluation::schedulePenalty
                        )
                        .thenComparingDouble(
                                NaturalRouteEvaluation::travelTimeMinutes
                        )
                        .thenComparingDouble(
                                NaturalRouteEvaluation::distanceKm
                        )
                        .thenComparing(NaturalRouteEvaluation::signature))
                .orElseThrow()
                .route();
    }

    private List<NaturalRouteEvaluation> evaluateNaturalRoutes(
            List<OptimizedPlaceDto> dailyPlaces,
            NaturalRouteContext context,
            LocalTime dailyStartTime,
            boolean walking,
            List<Integer> startPositions
    ) {
        List<NaturalRouteEvaluation> evaluations = new ArrayList<>();
        for (int startPosition : startPositions) {
            boolean[] used = new boolean[dailyPlaces.size()];
            used[startPosition] = true;
            List<Integer> route = new ArrayList<>();
            route.add(startPosition);
            buildNaturalRoutePermutations(
                    dailyPlaces,
                    context,
                    dailyStartTime,
                    walking,
                    route,
                    used,
                    evaluations
            );
        }
        return evaluations;
    }

    private void buildNaturalRoutePermutations(
            List<OptimizedPlaceDto> dailyPlaces,
            NaturalRouteContext context,
            LocalTime dailyStartTime,
            boolean walking,
            List<Integer> route,
            boolean[] used,
            List<NaturalRouteEvaluation> evaluations
    ) {
        if (route.size() == dailyPlaces.size()) {
            NaturalRouteEvaluation evaluation = evaluateNaturalRoute(
                    dailyPlaces,
                    context,
                    dailyStartTime,
                    walking,
                    route
            );
            if (evaluation != null) {
                evaluations.add(evaluation);
            }
            return;
        }

        for (int next = 0; next < dailyPlaces.size(); next++) {
            if (used[next]) {
                continue;
            }
            used[next] = true;
            route.add(next);
            buildNaturalRoutePermutations(
                    dailyPlaces,
                    context,
                    dailyStartTime,
                    walking,
                    route,
                    used,
                    evaluations
            );
            route.remove(route.size() - 1);
            used[next] = false;
        }
    }

    private NaturalRouteEvaluation evaluateNaturalRoute(
            List<OptimizedPlaceDto> dailyPlaces,
            NaturalRouteContext context,
            LocalTime dailyStartTime,
            boolean walking,
            List<Integer> route
    ) {
        double travelTimeMinutes = 0.0;
        double distanceKm = 0.0;
        for (int position = 1; position < route.size(); position++) {
            int previousMatrixIndex = context.matrixIndexes().get(
                    route.get(position - 1)
            );
            int currentMatrixIndex = context.matrixIndexes().get(
                    route.get(position)
            );
            double legMinutes = context.routeMatrix().getTravelTimeMinutes(
                    previousMatrixIndex,
                    currentMatrixIndex
            );
            if (walking
                    && (context.routeMatrix().isEstimated(
                    previousMatrixIndex,
                    currentMatrixIndex
            ) || legMinutes > WALKING_MAX_MINUTES + EPSILON)) {
                return null;
            }
            travelTimeMinutes += legMinutes;
            distanceKm += context.routeMatrix().getDistanceKm(
                    previousMatrixIndex,
                    currentMatrixIndex
            );
        }

        List<Integer> immutableRoute = List.copyOf(route);
        return new NaturalRouteEvaluation(
                immutableRoute,
                travelTimeMinutes,
                distanceKm,
                calculateNaturalSchedulePenalty(
                        dailyPlaces,
                        context,
                        dailyStartTime,
                        immutableRoute
                ),
                immutableRoute.stream()
                        .map(index -> dailyPlaces.get(index).getPlaceId())
                        .map(String::valueOf)
                        .reduce((left, right) -> left + ">" + right)
                        .orElse("")
        );
    }

    /** 점심 식당·연속 카테고리·오후 카페 규칙을 분 단위의 소프트 페널티로 환산한다. */
    private double calculateNaturalSchedulePenalty(
            List<OptimizedPlaceDto> dailyPlaces,
            NaturalRouteContext context,
            LocalTime dailyStartTime,
            List<Integer> route
    ) {
        double currentMinute = dailyStartTime.getHour() * 60.0
                + dailyStartTime.getMinute();
        Double firstRestaurantArrival = null;
        double penalty = 0.0;

        for (int position = 0; position < route.size(); position++) {
            int currentPosition = route.get(position);
            OptimizedPlaceDto current = dailyPlaces.get(currentPosition);
            if (position > 0) {
                int previousMatrixIndex = context.matrixIndexes().get(
                        route.get(position - 1)
                );
                int currentMatrixIndex = context.matrixIndexes().get(
                        currentPosition
                );
                currentMinute += context.routeMatrix().getTravelTimeMinutes(
                        previousMatrixIndex,
                        currentMatrixIndex
                );
            }

            if (position == 0 && !isCategory(current, "TOUR")) {
                penalty += 500.0;
            }
            if (isCategory(current, "RESTAURANT")
                    && firstRestaurantArrival == null) {
                firstRestaurantArrival = currentMinute;
            }
            if (isCategory(current, "CAFE")
                    && currentMinute < AFTERNOON_CAFE_MINUTE) {
                penalty += (AFTERNOON_CAFE_MINUTE - currentMinute) * 0.5;
            }

            if (position > 0) {
                OptimizedPlaceDto previous = dailyPlaces.get(
                        route.get(position - 1)
                );
                if (sameCategory(previous, current)
                        && (isCategory(current, "RESTAURANT")
                        || isCategory(current, "CAFE"))) {
                    penalty += 120.0;
                }
            }

            if (isCategory(current, "CAFE")) {
                boolean previousTour = position > 0
                        && isCategory(
                                dailyPlaces.get(route.get(position - 1)),
                                "TOUR"
                        );
                boolean nextTour = position + 1 < route.size()
                        && isCategory(
                                dailyPlaces.get(route.get(position + 1)),
                                "TOUR"
                        );
                if (!previousTour && !nextTour) {
                    penalty += 30.0;
                }
            }

            currentMinute += valueOrZero(current.getExpectedVisitMinutes());
        }

        if (firstRestaurantArrival != null) {
            if (firstRestaurantArrival < LUNCH_START_MINUTE) {
                penalty += (LUNCH_START_MINUTE - firstRestaurantArrival) * 1.2;
            } else if (firstRestaurantArrival > LUNCH_END_MINUTE) {
                penalty += (firstRestaurantArrival - LUNCH_END_MINUTE) * 1.2;
            } else {
                // 허용 시간대 안에서는 12시 30분에 가까울수록 아주 조금 우선한다.
                penalty += Math.abs(
                        firstRestaurantArrival - LUNCH_TARGET_MINUTE
                ) * 0.05;
            }
        }
        return penalty;
    }

    private List<Integer> createOrderedIndexes(int size) {
        List<Integer> indexes = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            indexes.add(index);
        }
        return indexes;
    }

    private boolean isCategory(OptimizedPlaceDto place, String category) {
        return place != null
                && place.getCategory() != null
                && category.equalsIgnoreCase(place.getCategory().trim());
    }

    private boolean sameCategory(
            OptimizedPlaceDto left,
            OptimizedPlaceDto right
    ) {
        return left != null
                && right != null
                && left.getCategory() != null
                && right.getCategory() != null
                && left.getCategory().trim().equalsIgnoreCase(
                        right.getCategory().trim()
                );
    }

    private OptimizedPlaceDto copyOptimizedPlaceWithRoute(
            OptimizedPlaceDto source,
            int visitOrder,
            double distanceKm,
            double travelMinutes,
            TransitPathType pathType,
            boolean estimated
    ) {
        return OptimizedPlaceDto.builder()
                .placeId(source.getPlaceId())
                .placeName(source.getPlaceName())
                .category(source.getCategory())
                .address(source.getAddress())
                .roadAddress(source.getRoadAddress())
                .imageUrl(source.getImageUrl())
                .recommendationScore(source.getRecommendationScore())
                .latitude(source.getLatitude())
                .longitude(source.getLongitude())
                .visitDate(source.getVisitDate())
                .themePalaceCultureYn(source.getThemePalaceCultureYn())
                .themeNatureHangangYn(source.getThemeNatureHangangYn())
                .themeDateYn(source.getThemeDateYn())
                .themeFoodTourYn(source.getThemeFoodTourYn())
                .themeCafeTourYn(source.getThemeCafeTourYn())
                .themeShoppingHotplaceYn(source.getThemeShoppingHotplaceYn())
                .themeNightViewYn(source.getThemeNightViewYn())
                .themeHotelStayYn(source.getThemeHotelStayYn())
                .expectedVisitMinutes(source.getExpectedVisitMinutes())
                .visitOrder(visitOrder)
                .distanceFromPreviousKm(distanceKm)
                .travelTimeFromPreviousMinutes(travelMinutes)
                .transitPathType(pathType)
                .routeEstimated(estimated)
                .build();
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

    /**
     * 이동비용을 후보 행렬의 실제 범위 기준 0~1로 환산한다.
     * 가장 짧은 값은 1, 가장 긴 값은 0이며 모든 값이 같으면 차이가 없으므로 1을 반환한다.
     */
    private double normalizeRouteCost(
            double value,
            double minimum,
            double maximum
    ) {
        if (Math.abs(maximum - minimum) < EPSILON) {
            return 1.0;
        }
        double normalized = (maximum - value) / (maximum - minimum);
        return Math.max(0.0, Math.min(1.0, normalized));
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
            ScoreRange scoreRange,
            RouteCostRange routeCostRange
    ) {
    }

    private record NaturalRouteContext(
            RouteMatrix routeMatrix,
            List<Integer> matrixIndexes
    ) {
    }

    private record NaturalRouteEvaluation(
            List<Integer> route,
            double travelTimeMinutes,
            double distanceKm,
            double schedulePenalty,
            String signature
    ) {
    }

    private record SequentialSelection(
            List<PlaceCandidateDto> placeCandidates,
            Map<LocalDate, Long> preferredFirstPlaceIdsByDate
    ) {
    }

    private record WalkingPathState(
            List<PlaceCandidateDto> path,
            Set<Long> selectedIds,
            Map<String, Integer> remainingTargets,
            double recommendationScore,
            double travelTimeMinutes,
            double distanceKm
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

    private record WalkingViolation(
            LocalDate visitDate,
            Long previousPlaceId,
            Long fromPlaceId,
            Long toPlaceId,
            Long nextPlaceId,
            double previousMinutes,
            double travelMinutes,
            double nextMinutes,
            boolean estimated
    ) {
    }

    private record WalkingReplacementTarget(
            Long replacedPlaceId,
            Long previousPlaceId,
            Long nextPlaceId,
            double baselineMaximumMinutes
    ) {
    }

    private record WalkingReplacementEvaluation(
            PlaceCandidateDto candidate,
            double maximumMinutes,
            double totalMinutes
    ) {
    }

    private record WalkingReplacement(
            Long replacedPlaceId,
            PlaceCandidateDto candidate,
            double maximumMinutes,
            double totalMinutes
    ) {
    }

    private record WalkingRemovalChoice(
            List<PlaceCandidateDto> retainedCandidates,
            Map<LocalDate, Long> preferredFirstPlaceIds,
            CourseOptimizeResponse optimized,
            WalkingRouteQuality quality,
            Long removedPlaceId,
            double removedRecommendationScore,
            int remainingDayPlaceCount
    ) {
    }

    private record WalkingHardLimitResult(
            CourseOptimizeResponse optimized,
            WalkingRouteQuality quality,
            int removedPlaces
    ) {
    }

    private record WalkingRouteQuality(
            int violationCount,
            int estimatedLegCount,
            int actualLegCount,
            double maximumTravelMinutes,
            double totalTravelMinutes
    ) {
    }

    private record HotelEvaluation(
            PlaceCandidateDto hotel,
            double averageDistanceKm,
            double maximumTravelMinutes,
            double averageTravelMinutes,
            boolean estimated
    ) {
    }

    private record RouteCostRange(
            double minimumTravelTimeMinutes,
            double maximumTravelTimeMinutes,
            double minimumDistanceKm,
            double maximumDistanceKm
    ) {
        static RouteCostRange from(RouteMatrix routeMatrix) {
            double minimumTravelTimeMinutes = Double.POSITIVE_INFINITY;
            double maximumTravelTimeMinutes = Double.NEGATIVE_INFINITY;
            double minimumDistanceKm = Double.POSITIVE_INFINITY;
            double maximumDistanceKm = Double.NEGATIVE_INFINITY;

            for (int fromIndex = 0; fromIndex < routeMatrix.size(); fromIndex++) {
                for (int toIndex = 0; toIndex < routeMatrix.size(); toIndex++) {
                    if (fromIndex == toIndex) {
                        continue;
                    }
                    double travelTimeMinutes = routeMatrix.getTravelTimeMinutes(
                            fromIndex,
                            toIndex
                    );
                    double distanceKm = routeMatrix.getDistanceKm(
                            fromIndex,
                            toIndex
                    );
                    if (Double.isFinite(travelTimeMinutes)) {
                        minimumTravelTimeMinutes = Math.min(
                                minimumTravelTimeMinutes,
                                travelTimeMinutes
                        );
                        maximumTravelTimeMinutes = Math.max(
                                maximumTravelTimeMinutes,
                                travelTimeMinutes
                        );
                    }
                    if (Double.isFinite(distanceKm)) {
                        minimumDistanceKm = Math.min(
                                minimumDistanceKm,
                                distanceKm
                        );
                        maximumDistanceKm = Math.max(
                                maximumDistanceKm,
                                distanceKm
                        );
                    }
                }
            }

            if (!Double.isFinite(minimumTravelTimeMinutes)) {
                minimumTravelTimeMinutes = 0.0;
                maximumTravelTimeMinutes = 0.0;
            }
            if (!Double.isFinite(minimumDistanceKm)) {
                minimumDistanceKm = 0.0;
                maximumDistanceKm = 0.0;
            }
            return new RouteCostRange(
                    minimumTravelTimeMinutes,
                    maximumTravelTimeMinutes,
                    minimumDistanceKm,
                    maximumDistanceKm
            );
        }
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
