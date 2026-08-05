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
import com.seoulink.backend.domain.course.exception.CourseRecommendationUnavailableException;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    // 다른 추천 코스는 날짜가 같거나 달라도 DAY 조합 한 쌍당 최대 1개까지 허용한다.
    // 숙소를 제외한 하루 장소 구성이 완전히 같으면 중복 상한 완화 중에도 허용하지 않는다.
    private static final int MAX_DAILY_OVERLAP_LIMIT = 1;
    // 도보만 후보 부족 시 1곳 → 2곳 → 최대 3곳까지 단계적으로 완화한다.
    private static final int MAX_WALKING_RELAXED_DAILY_OVERLAP_LIMIT = 3;
    /*
     * 이동 최소를 먼저 생성해야 앞선 옵션이 가까운 장소를 모두 선점하는 편향을
     * 피할 수 있다. 응답 화면에서는 아래에서 다시 취향 집중 → 이동 최소 → 균형
     * 순서로 정렬한다.
     */
    private static final List<OptionStrategy> OPTION_GENERATION_ORDER = List.of(
            OptionStrategy.MIN_DISTANCE,
            OptionStrategy.PREFERENCE,
            OptionStrategy.BALANCED
    );
    private static final int MAX_DUPLICATE_RECOMMENDATION_ATTEMPTS = 20;
    private static final double PREVIOUSLY_RECOMMENDED_PENALTY = 20.0;
    private static final double NEW_PLACE_BONUS = 5.0;
    private static final int MAX_PREFERRED_REGIONS = 5;
    private static final List<Double> PREFERRED_REGION_BONUSES =
            List.of(8.0, 6.0, 4.0, 2.0, 1.0);
    private static final Pattern DISTRICT_PATTERN =
            Pattern.compile("([가-힣]+구)");
    private static final double DISPLAY_SCORE_MIN = 70.0;
    private static final double DISPLAY_SCORE_MAX = 95.0;
    private static final double EQUAL_SCORE_DISPLAY_VALUE = 85.0;
    private static final double EPSILON = 0.000000001;
    private static final double WALKING_MAX_MINUTES = 20.0;
    private static final double WALKING_TARGET_AVERAGE_MINUTES = 15.0;
    private static final double WALKING_RELAXED_AVERAGE_MINUTES = 18.0;
    // 숙소 도착은 20분 이내를 우선하고 최대 30분까지 완화한다.
    // DAY2 이후 숙소 출발 구간은 일반 도보 구간과 동일하게 20분을 넘길 수 없다.
    private static final double HOTEL_WALKING_PREFERRED_MAX_MINUTES = 20.0;
    private static final double HOTEL_WALKING_MAX_MINUTES = 30.0;
    private static final double HOTEL_DEPARTURE_WALKING_MAX_MINUTES =
            WALKING_MAX_MINUTES;
    private static final int MIN_PLACES_PER_DAY = 3;
    /*
     * 후보 생성은 하루 최소 3곳을 유지한다. 다만 추정값과 실제 ORS 결과가 달라
     * 3곳 경로가 20분 상한을 넘는 경우에는 요청 전체를 실패시키지 않도록 실제
     * 경로 복구 단계에서만 최후 수단으로 2곳까지 줄인다.
     */
    private static final int MIN_ACTUAL_WALKING_REPAIR_PLACES_PER_DAY = 2;
    private static final int WALKING_PATH_BEAM_WIDTH = 64;
    private static final int MAX_WALKING_OPTION_CANDIDATES_PER_STRATEGY = 8;
    private static final int MAX_WALKING_CANDIDATE_GENERATION_ATTEMPTS = 36;
    private static final int EXPANDED_WALKING_OPTION_CANDIDATES_PER_STRATEGY =
            16;
    private static final int EXPANDED_WALKING_CANDIDATE_GENERATION_ATTEMPTS =
            84;
    private static final int MAX_WALKING_EXCLUSION_DEPTH = 60;
    private static final int MAX_WALKING_JOINT_COMBINATIONS = 800;
    // 추정 경로와 실제 ORS 경로의 차이로 20분을 넘는 구간이 생기면
    // 먼저 현재 순서에서 장소를 줄여 복구하고, 그래도 세 코스 조합이 없으면
    // 문제 DAY의 목표 장소 수를 한 곳 낮춰 후보부터 다시 고른다.
    private static final int MAX_ACTUAL_WALKING_REPAIR_ATTEMPTS = 8;
    private static final int MAX_ACTUAL_WALKING_REPAIR_DEPTH = 2;

    // 일정 흐름 보정은 동선을 과도하게 늘리지 않는 범위에서만 적용한다.
    private static final int LUNCH_START_MINUTE = 11 * 60 + 30;
    private static final int LUNCH_END_MINUTE = 14 * 60;
    private static final int LUNCH_TARGET_MINUTE = 12 * 60 + 30;
    private static final int DINNER_START_MINUTE = 17 * 60 + 30;
    private static final int DINNER_END_MINUTE = 19 * 60 + 30;
    private static final int DINNER_TARGET_MINUTE = 18 * 60 + 30;
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
     * <p>같은 코스의 앞 DAY에서 실제 선택된 장소는 이후 DAY에서 완전히 제외한다.
     * 서로 다른 옵션은 날짜가 같거나 달라도 모든 DAY 조합을 비교하고, 옵션 한 쌍의
     * DAY 조합마다 기본 최대 1개까지만 일반 장소 중복을 허용한다. 후보가 부족하면
     * 중복 수만 단계적으로 완화할 수 있지만, 숙소를 제외한 하루 장소 구성이 완전히
     * 같은 경우와 제외된 이전 추천 코스와 완전히 같은 경우는 끝까지 반환하지 않는다.</p>
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
        if (validated.transportMode() == TransportMode.WALKING) {
            return recommendWalkingJointly(
                    request,
                    validated,
                    routeContexts,
                    dailyOverlapLimit
            );
        }

        List<CourseOptionResponse> courseOptions = new ArrayList<>();
        /*
         * 같은 호출에서 생성하는 세 옵션끼리만 같은 DAY 중복 상한을 검사한다.
         * 재추천의 이전 장소는 새 후보 풀 조회와 previouslyRecommendedPlaceIds에서
         * 이미 우선 제외·감점되므로, 여기까지 이전 옵션을 다시 넣으면 제한이
         * 이중 적용되어 세 번째 옵션이 통째로 사라질 수 있다.
         */
        List<Map<LocalDate, Set<Long>>> generatedOptionPlaces =
                new ArrayList<>();
        Map<LocalDate, Set<Long>> usedFirstPlaceIdsByDate = new TreeMap<>();
        Set<Long> usedHotelIds = new LinkedHashSet<>();
        Set<String> generatedRecommendationKeys = new LinkedHashSet<>();
        int optionNo = 1;

        for (OptionStrategy strategy : OPTION_GENERATION_ORDER) {
            boolean walking = validated.transportMode() == TransportMode.WALKING;
            Set<Long> retryHardExcludedPlaceIds = new LinkedHashSet<>();
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
                        retryHardExcludedPlaceIds
                );

                SequentialSelection selection;
                try {
                    selection = walking
                            ? createWalkingSequentialSelection(
                            routeContexts,
                            strategy,
                            attemptPreviouslyRecommendedPlaceIds,
                            generatedOptionPlaces,
                            dailyOverlapLimit,
                            request.getResultId(),
                            retryHardExcludedPlaceIds
                    )
                            : createSequentialSelection(
                            routeContexts,
                            strategy,
                            attemptPreviouslyRecommendedPlaceIds,
                            generatedOptionPlaces,
                            dailyOverlapLimit,
                            retryHardExcludedPlaceIds
                    );
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    log.warn(
                            "중복·이동 제한을 만족하는 추천 코스를 만들 수 없어 옵션을 제외합니다: "
                                    + "resultId={}, strategy={}, reason={}",
                            request.getResultId(),
                            strategy,
                            exception.getMessage()
                    );
                    selection = null;
                }
                if (selection == null) {
                    duplicateRecommendation = true;
                    break;
                }

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

                // 최종 장소 구성은 유지하면서 관광지 시작·점심·저녁 식당·카페
                // 흐름을 기존 동선이 크게 나빠지지 않는 범위에서 방문 순서에 반영한다.
                optimized = applyNaturalScheduleFlow(
                        optimized,
                        request.getDailyStartTime(),
                        validated.scheduleType(),
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

                /*
                 * 후보 선발 단계에서 중복을 제한해도 이후 최적화·대체 후보 적용·일정 흐름
                 * 보정 과정에서 최종 장소 구성이 달라질 수 있다. 따라서 사용자에게 실제로
                 * 내려갈 장소 ID를 기준으로 한 번 더 검사한다.
                 *
                 * 같은 코스의 다른 DAY가 한 곳이라도 겹치거나, 같은 DAY의 다른 옵션과
                 * 두 곳 이상 겹치면 초과 중복 장소를 현재 옵션에서 완전히 제외하고 다시
                 * 생성한다. 재생성에도 실패하면 잘못된 옵션을 그대로 응답하지 않는다.
                 */
                Map<LocalDate, Set<Long>> finalOrdinaryPlacesByDate =
                        ordinaryPlaceIdsByDate(optimized);
                FinalOverlapViolation finalOverlapViolation =
                        findFinalOverlapViolation(
                                finalOrdinaryPlacesByDate,
                                generatedOptionPlaces,
                                dailyOverlapLimit
                        );
                if (finalOverlapViolation != null) {
                    Set<Long> newlyExcludedPlaceIds =
                            finalOverlapViolation.placeIdsToExclude().stream()
                                    .filter(placeId -> !retryHardExcludedPlaceIds
                                            .contains(placeId))
                                    .filter(placeId -> routeContexts.values().stream()
                                            .anyMatch(context -> context
                                                    .candidateIndexes()
                                                    .containsKey(placeId)))
                                    .collect(java.util.stream.Collectors.toCollection(
                                            LinkedHashSet::new
                                    ));

                    duplicateRecommendation = true;
                    if (newlyExcludedPlaceIds.isEmpty()) {
                        log.warn(
                                "최종 중복 제한 위반을 해결할 추가 제외 장소가 없어 "
                                        + "옵션을 반환하지 않습니다: resultId={}, "
                                        + "strategy={}, reason={}",
                                request.getResultId(),
                                strategy,
                                finalOverlapViolation.reason()
                        );
                        break;
                    }

                    retryHardExcludedPlaceIds.addAll(newlyExcludedPlaceIds);
                    log.warn(
                            "최종 장소 기준 중복 제한 위반으로 옵션을 재생성합니다: "
                                    + "resultId={}, strategy={}, attempt={}/{}, "
                                    + "reason={}, hardExcludedPlaceIds={}",
                            request.getResultId(),
                            strategy,
                            attempt,
                            MAX_DUPLICATE_RECOMMENDATION_ATTEMPTS,
                            finalOverlapViolation.reason(),
                            newlyExcludedPlaceIds
                    );
                    continue;
                }

                String ordinaryRecommendationKey =
                        createOrdinaryOptimizedCompositionSignature(
                                optimized.getOptimizedPlaces(),
                                validated.transportMode()
                        );
                // 숙소가 바뀌어도 같은 일반 장소 코스를 다시 반환하지 않도록
                // 외부에 전달하는 제외 키 역시 숙소를 제거한 최종 장소 기준으로 만든다.
                recommendationKey = ordinaryRecommendationKey;
                boolean duplicatedInCurrentResponse =
                        generatedRecommendationKeys.contains(
                                ordinaryRecommendationKey
                        );
                boolean duplicatedInPreviousResponse =
                        isExcludedRecommendationComposition(
                                ordinaryRecommendationKey,
                                validated.excludedRecommendationKeys(),
                                validated.hotelCandidates()
                        );

                if (!duplicatedInCurrentResponse
                        && !duplicatedInPreviousResponse) {
                    duplicateRecommendation = false;
                    break;
                }

                duplicateRecommendation = true;

                // 같은 계산을 반복하지 않도록 다음 시도에서는 중복 코스의 일반 장소 중
                // 아직 막지 않은 한 곳을 실제 후보 선발 단계에서 완전히 제외한다.
                Long retryExcludedPlaceId = nextRetryExcludedPlaceId(
                        ordinaryRecommendationKey,
                        retryHardExcludedPlaceIds,
                        routeContexts
                );
                if (retryExcludedPlaceId == null) {
                    break;
                }
                retryHardExcludedPlaceIds.add(retryExcludedPlaceId);
                log.info(
                        "중복 코스 재생성 시도: resultId={}, strategy={}, "
                                + "attempt={}/{}, hardExcludedPlaceId={}, "
                                + "recommendationKey={}",
                        request.getResultId(),
                        strategy,
                        attempt,
                        MAX_DUPLICATE_RECOMMENDATION_ATTEMPTS,
                        retryExcludedPlaceId,
                        recommendationKey
                );
            }

            if (duplicateRecommendation || optimized == null
                    || recommendationKey == null) {
                throw new CourseRecommendationUnavailableException(
                        "세 가지 추천 코스를 모두 생성하지 못했습니다. "
                                + "strategy=" + strategy
                                + ", generatedOptions=" + courseOptions.size()
                );
            }
            generatedRecommendationKeys.add(
                    createOrdinaryOptimizedCompositionSignature(
                            optimized.getOptimizedPlaces(),
                            validated.transportMode()
                    )
            );
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
                    recommendationKey,
                    validated.dailyPlans().size() >= 2,
                    selectedHotelId != null,
                    requestedPlaceCountsByDate(routeContexts)
            ));
        }

        if (courseOptions.size() != OPTION_GENERATION_ORDER.size()) {
            throw new CourseRecommendationUnavailableException(
                    "추천 코스는 항상 3개여야 합니다. actual="
                            + courseOptions.size()
            );
        }

        normalizeFinalOptionRoles(courseOptions);

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
                .preferredRegions(validated.preferredRegions())
                .estimatedTravelTimes(courseOptions.stream()
                        .anyMatch(option -> Boolean.TRUE.equals(
                                option.getEstimatedTravelTimes()
                        )))
                .dailyStartTime(request.getDailyStartTime())
                .optionCount(courseOptions.size())
                .courseOptions(courseOptions)
                .build();
    }

    /**
     * 도보는 전략 하나를 먼저 확정하지 않고 각 전략의 유효 후보를 여러 개 만든 뒤
     * 세 옵션 조합을 함께 선택한다. 마지막 BALANCED 옵션이 앞 옵션의 남은 장소만
     * 받아 사라지는 순차 확정 문제를 막는다.
     */
    private CourseRecommendResponse recommendWalkingJointly(
            CourseRecommendRequest request,
            ValidatedRecommendation validated,
            Map<LocalDate, DailyRouteContext> routeContexts,
            int dailyOverlapLimit
    ) {
        return recommendWalkingJointly(
                request,
                validated,
                routeContexts,
                dailyOverlapLimit,
                requestedPlaceCountsByDate(routeContexts),
                new LinkedHashSet<>()
        );
    }

    /**
     * 실제 ORS 검증에서 실패한 DAY는 목표 장소 수를 한 곳씩 줄여 후보 생성부터
     * 다시 수행한다. 응답의 requestedPlaceCount는 최초 요청값을 유지한다.
     */
    private CourseRecommendResponse recommendWalkingJointly(
            CourseRecommendRequest request,
            ValidatedRecommendation validated,
            Map<LocalDate, DailyRouteContext> routeContexts,
            int dailyOverlapLimit,
            Map<LocalDate, Integer> originalRequestedPlaceCounts,
            Set<String> attemptedTargetSignatures
    ) {
        String targetSignature = walkingTargetSignature(routeContexts);
        if (!attemptedTargetSignatures.add(targetSignature)) {
            throw new CourseRecommendationUnavailableException(
                    "같은 도보 장소 수 조합을 반복해 재시도할 수 없습니다. "
                            + "resultId=" + request.getResultId()
                            + ", targets=" + targetSignature
            );
        }

        Map<OptionStrategy, List<WalkingOptionCandidate>> candidatesByStrategy =
                new LinkedHashMap<>();
        for (OptionStrategy strategy : OptionStrategy.values()) {
            candidatesByStrategy.put(
                    strategy,
                    generateWalkingOptionCandidates(
                            request,
                            validated,
                            routeContexts,
                            dailyOverlapLimit,
                            strategy,
                            MAX_WALKING_OPTION_CANDIDATES_PER_STRATEGY,
                            MAX_WALKING_CANDIDATE_GENERATION_ATTEMPTS
                    )
            );
        }

        log.info(
                "도보 옵션 후보 생성 완료: resultId={}, preference={}, "
                        + "minimumDistance={}, balanced={}",
                request.getResultId(),
                candidatesByStrategy.get(OptionStrategy.PREFERENCE).size(),
                candidatesByStrategy.get(OptionStrategy.MIN_DISTANCE).size(),
                candidatesByStrategy.get(OptionStrategy.BALANCED).size()
        );

        WalkingCombinationSelection combinationSelection =
                selectWalkingCandidateCombinations(
                        candidatesByStrategy,
                        dailyOverlapLimit
                );
        if (combinationSelection.combinations().isEmpty()) {
            log.warn(
                    "최초 도보 후보 조합이 모두 중복 제한에 걸려 확장 탐색합니다: "
                            + "resultId={}, initialCandidateCounts={}",
                    request.getResultId(),
                    walkingCandidateCounts(candidatesByStrategy)
            );
            for (OptionStrategy strategy : OptionStrategy.values()) {
                List<WalkingOptionCandidate> expanded =
                        generateWalkingOptionCandidates(
                                request,
                                validated,
                                routeContexts,
                                dailyOverlapLimit,
                                strategy,
                                EXPANDED_WALKING_OPTION_CANDIDATES_PER_STRATEGY,
                                EXPANDED_WALKING_CANDIDATE_GENERATION_ATTEMPTS
                        );
                candidatesByStrategy.put(strategy, expanded);
                combinationSelection = selectWalkingCandidateCombinations(
                        candidatesByStrategy,
                        dailyOverlapLimit
                );
                if (!combinationSelection.combinations().isEmpty()) {
                    log.info(
                            "도보 후보 확장으로 세 코스 조합을 찾았습니다: "
                                    + "resultId={}, expandedStrategy={}, "
                                    + "candidateCounts={}",
                            request.getResultId(),
                            strategy,
                            walkingCandidateCounts(candidatesByStrategy)
                    );
                    break;
                }
            }
        }

        if (combinationSelection.combinations().isEmpty()) {
            combinationSelection = findRelaxedWalkingCombinationSelection(
                    candidatesByStrategy,
                    dailyOverlapLimit,
                    request.getResultId()
            );
        }
        if (combinationSelection.combinations().isEmpty()) {
            throw new CourseRecommendationUnavailableException(
                    "도보 시간 제한을 유지한 세 코스 후보를 만들 수 없습니다. "
                            + "resultId=" + request.getResultId()
                            + ", candidateCounts="
                            + walkingCandidateCounts(candidatesByStrategy)
            );
        }

        Map<String, CourseOptimizeResponse> actualRouteCache =
                new LinkedHashMap<>();
        Set<String> invalidActualRouteKeys = new LinkedHashSet<>();
        Set<LocalDate> actualLimitFailureDates = new LinkedHashSet<>();
        Set<String> attemptedActualCombinationKeys = new LinkedHashSet<>();

        ResolvedWalkingResponse bestReducedResponse = null;
        ResolvedWalkingResponse resolvedResponse =
                tryWalkingCombinationSelection(
                        request,
                        validated,
                        routeContexts,
                        originalRequestedPlaceCounts,
                        combinationSelection,
                        actualRouteCache,
                        invalidActualRouteKeys,
                        actualLimitFailureDates,
                        attemptedActualCombinationKeys
                );
        if (resolvedResponse != null) {
            if (resolvedResponse.placeCountShortfall() == 0) {
                return resolvedResponse.response();
            }
            bestReducedResponse = resolvedResponse;
        }

        /*
         * 추정 경로에서 기본 중복 상한을 만족한 조합이 실제 ORS 검증에서
         * 탈락했더라도 바로 장소 수를 줄이지 않는다. 같은 후보 안에서 도보 전용
         * 중복 상한 2→3을 먼저 확인하면 장소 수를 유지한 유효 조합을 찾을 수 있다.
         */
        for (int relaxedOverlap =
                combinationSelection.dailyOverlapLimit() + 1;
             relaxedOverlap <= MAX_WALKING_RELAXED_DAILY_OVERLAP_LIMIT;
             relaxedOverlap++) {
            WalkingCombinationSelection relaxedSelection =
                    selectWalkingCandidateCombinations(
                            candidatesByStrategy,
                            relaxedOverlap
                    );
            resolvedResponse = tryWalkingCombinationSelection(
                    request,
                    validated,
                    routeContexts,
                    originalRequestedPlaceCounts,
                    relaxedSelection,
                    actualRouteCache,
                    invalidActualRouteKeys,
                    actualLimitFailureDates,
                    attemptedActualCombinationKeys
            );
            if (resolvedResponse != null) {
                if (resolvedResponse.placeCountShortfall() == 0) {
                    log.warn(
                            "실제 도보 경로 검증 후 장소 수를 유지하기 위해 "
                                    + "옵션 간 중복 상한만 완화했습니다: "
                                    + "resultId={}, overlapLimit={}",
                            request.getResultId(),
                            relaxedOverlap
                    );
                    return resolvedResponse.response();
                }
                bestReducedResponse = betterWalkingResponse(
                        bestReducedResponse,
                        resolvedResponse
                );
            }
        }

        /*
         * 초기 8개 후보가 실제 경로에서 모두 탈락한 경우에는 전략별 후보를
         * 최대 16개까지 다시 넓힌다. 확장 후보에서도 중복 1→2→3 순서로
         * 확인하고, 이 과정까지 실패한 DAY만 마지막에 장소 수를 줄인다.
         */
        for (OptionStrategy strategy : OptionStrategy.values()) {
            candidatesByStrategy.put(
                    strategy,
                    generateWalkingOptionCandidates(
                            request,
                            validated,
                            routeContexts,
                            dailyOverlapLimit,
                            strategy,
                            EXPANDED_WALKING_OPTION_CANDIDATES_PER_STRATEGY,
                            EXPANDED_WALKING_CANDIDATE_GENERATION_ATTEMPTS
                    )
            );
        }
        log.warn(
                "실제 도보 경로 검증 실패 후 장소 수 축소 전에 후보를 확장합니다: "
                        + "resultId={}, candidateCounts={}",
                request.getResultId(),
                walkingCandidateCounts(candidatesByStrategy)
        );

        for (int overlapLimit = dailyOverlapLimit;
             overlapLimit <= MAX_WALKING_RELAXED_DAILY_OVERLAP_LIMIT;
             overlapLimit++) {
            WalkingCombinationSelection expandedSelection =
                    selectWalkingCandidateCombinations(
                            candidatesByStrategy,
                            overlapLimit
                    );
            resolvedResponse = tryWalkingCombinationSelection(
                    request,
                    validated,
                    routeContexts,
                    originalRequestedPlaceCounts,
                    expandedSelection,
                    actualRouteCache,
                    invalidActualRouteKeys,
                    actualLimitFailureDates,
                    attemptedActualCombinationKeys
            );
            if (resolvedResponse != null) {
                if (resolvedResponse.placeCountShortfall() == 0) {
                    if (overlapLimit > dailyOverlapLimit) {
                        log.warn(
                                "확장 후보의 실제 도보 경로를 사용해 장소 수는 "
                                        + "유지하고 중복 상한만 완화했습니다: "
                                        + "resultId={}, overlapLimit={}",
                                request.getResultId(),
                                overlapLimit
                        );
                    }
                    return resolvedResponse.response();
                }
                bestReducedResponse = betterWalkingResponse(
                        bestReducedResponse,
                        resolvedResponse
                );
            }
        }

        if (bestReducedResponse != null) {
            log.warn(
                    "후보 확장과 중복 상한 완화를 모두 확인한 뒤에만 실제 "
                            + "도보 제한으로 장소 수가 조정된 응답을 사용합니다: "
                            + "resultId={}, placeCountShortfall={}, "
                            + "overlapLimit={}",
                    request.getResultId(),
                    bestReducedResponse.placeCountShortfall(),
                    bestReducedResponse.dailyOverlapLimit()
            );
            return bestReducedResponse.response();
        }

        Map<LocalDate, DailyRouteContext> reducedRouteContexts =
                reduceWalkingRouteContextsAfterActualFailure(
                        routeContexts,
                        actualLimitFailureDates,
                        validated.scheduleType(),
                        request.getResultId()
                );
        if (!walkingTargetSignature(reducedRouteContexts).equals(
                targetSignature
        )) {
            CourseRecommendResponse reducedResponse =
                    recommendWalkingJointly(
                            request,
                            validated,
                            reducedRouteContexts,
                            dailyOverlapLimit,
                            originalRequestedPlaceCounts,
                            attemptedTargetSignatures
                    );
            log.info(
                    "실제 도보 제한 실패 DAY의 장소 수 축소 재선택 완료: "
                            + "resultId={}, failedDates={}, beforeTargets={}, "
                            + "afterTargets={}",
                    request.getResultId(),
                    actualLimitFailureDates,
                    targetSignature,
                    walkingTargetSignature(reducedRouteContexts)
            );
            return reducedResponse;
        }

        throw new CourseRecommendationUnavailableException(
                "후보 풀 확장과 세 코스 전체 재선택 후에도 실제 도보 제한을 "
                        + "만족하는 조합을 만들 수 없습니다. resultId="
                        + request.getResultId()
                        + ", attemptedCombinations="
                        + attemptedActualCombinationKeys.size()
                        + ", actualValidatedCandidates="
                        + actualRouteCache.size()
                        + ", invalidActualCandidates="
                        + invalidActualRouteKeys.size()
        );
    }

    /**
     * 한 중복 상한에서 만든 세 코스 조합을 실제 ORS 경로로 검증한다.
     * 같은 후보는 호출 전체 캐시를 공유하고, 같은 조합도 중복 상한별 한 번만
     * 검사해 후보 확장·완화 단계의 외부 호출이 불필요하게 반복되지 않게 한다.
     */
    private ResolvedWalkingResponse tryWalkingCombinationSelection(
            CourseRecommendRequest request,
            ValidatedRecommendation validated,
            Map<LocalDate, DailyRouteContext> routeContexts,
            Map<LocalDate, Integer> originalRequestedPlaceCounts,
            WalkingCombinationSelection selection,
            Map<String, CourseOptimizeResponse> actualRouteCache,
            Set<String> invalidActualRouteKeys,
            Set<LocalDate> actualLimitFailureDates,
            Set<String> attemptedCombinationKeys
    ) {
        if (selection == null || selection.combinations().isEmpty()) {
            return null;
        }

        int overlapLimit = selection.dailyOverlapLimit();
        ResolvedWalkingResponse bestReducedResponse = null;
        for (WalkingCandidateCombination combination
                : selection.combinations()) {
            String attemptKey = overlapLimit + ":" + combination.signature();
            if (!attemptedCombinationKeys.add(attemptKey)) {
                continue;
            }

            List<ResolvedWalkingCandidate> resolvedCandidates =
                    new ArrayList<>();
            boolean invalidCombination = false;
            for (WalkingOptionCandidate candidate
                    : combination.candidates()) {
                CourseOptimizeResponse actual = resolveActualWalkingCandidate(
                        candidate,
                        request.getResultId(),
                        actualRouteCache,
                        invalidActualRouteKeys,
                        actualLimitFailureDates
                );
                if (actual == null) {
                    invalidCombination = true;
                    break;
                }
                resolvedCandidates.add(new ResolvedWalkingCandidate(
                        candidate,
                        actual
                ));
            }
            if (invalidCombination) {
                continue;
            }

            CourseRecommendResponse response = buildWalkingJointResponse(
                    request,
                    validated,
                    routeContexts,
                    overlapLimit,
                    resolvedCandidates,
                    originalRequestedPlaceCounts
            );
            if (response == null) {
                continue;
            }
            int placeCountShortfall =
                    calculateWalkingResponsePlaceCountShortfall(
                            response,
                            originalRequestedPlaceCounts
                    );
            ResolvedWalkingResponse resolved = new ResolvedWalkingResponse(
                    response,
                    placeCountShortfall,
                    overlapLimit
            );
            if (placeCountShortfall > 0) {
                bestReducedResponse = betterWalkingResponse(
                        bestReducedResponse,
                        resolved
                );
                continue;
            }

            log.info(
                    "도보 세 코스 동시 선택 완료: resultId={}, "
                            + "attemptedCombinations={}, "
                            + "actualValidatedCandidates={}, "
                            + "maximumDailyOverlap={}, "
                            + "walkingAverageLimit={}",
                    request.getResultId(),
                    attemptedCombinationKeys.size(),
                    actualRouteCache.size(),
                    overlapLimit,
                    WALKING_RELAXED_AVERAGE_MINUTES
            );
            return resolved;
        }
        return bestReducedResponse;
    }

    private int calculateWalkingResponsePlaceCountShortfall(
            CourseRecommendResponse response,
            Map<LocalDate, Integer> originalRequestedPlaceCounts
    ) {
        if (response == null || response.getCourseOptions() == null) {
            return Integer.MAX_VALUE;
        }

        int shortfall = 0;
        for (CourseOptionResponse option : response.getCourseOptions()) {
            if (option == null || option.getDays() == null) {
                continue;
            }
            for (CourseDayResponse day : option.getDays()) {
                if (day == null || day.getVisitDate() == null) {
                    continue;
                }
                int actualPlaceCount = day.getPlaces() == null
                        ? 0
                        : (int) day.getPlaces().stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(place -> !"HOTEL".equalsIgnoreCase(
                                place.getCategory()
                        ))
                        .count();
                int requestedPlaceCount =
                        originalRequestedPlaceCounts.getOrDefault(
                                day.getVisitDate(),
                                actualPlaceCount
                        );
                shortfall += Math.max(
                        0,
                        requestedPlaceCount - actualPlaceCount
                );
            }
        }
        return shortfall;
    }

    private ResolvedWalkingResponse betterWalkingResponse(
            ResolvedWalkingResponse current,
            ResolvedWalkingResponse candidate
    ) {
        if (candidate == null) {
            return current;
        }
        if (current == null
                || candidate.placeCountShortfall()
                < current.placeCountShortfall()
                || candidate.placeCountShortfall()
                == current.placeCountShortfall()
                && candidate.dailyOverlapLimit()
                < current.dailyOverlapLimit()) {
            return candidate;
        }
        return current;
    }

    /**
     * 한 전략에서 서로 다른 도보 코스 후보를 최대 8개 만든다.
     * 첫 경로의 장소를 하나씩 제외한 분기를 다시 탐색해 같은 최상위 조합만
     * 반복하는 것을 막는다.
     */
    private List<WalkingOptionCandidate> generateWalkingOptionCandidates(
            CourseRecommendRequest request,
            ValidatedRecommendation validated,
            Map<LocalDate, DailyRouteContext> routeContexts,
            int dailyOverlapLimit,
            OptionStrategy strategy,
            int maximumCandidateCount,
            int maximumAttempts
    ) {
        Deque<Set<Long>> pendingExclusions = new ArrayDeque<>();
        pendingExclusions.add(Set.of());
        Set<String> attemptedExclusionKeys = new LinkedHashSet<>();
        Map<String, WalkingOptionCandidate> uniqueCandidates =
                new LinkedHashMap<>();
        int attempts = 0;

        while (!pendingExclusions.isEmpty()
                && attempts < maximumAttempts
                && uniqueCandidates.size()
                < maximumCandidateCount) {
            Set<Long> hardExcludedPlaceIds =
                    pendingExclusions.removeFirst();
            String exclusionKey = placeIdSetSignature(
                    hardExcludedPlaceIds
            );
            if (!attemptedExclusionKeys.add(exclusionKey)) {
                continue;
            }
            attempts++;

            List<Boolean> traversalOrders = routeContexts.size() > 1
                    ? List.of(false, true)
                    : List.of(false);
            for (boolean reverseDateOrder : traversalOrders) {
                if (uniqueCandidates.size()
                        >= maximumCandidateCount) {
                    break;
                }

                SequentialSelection selection;
                try {
                    selection = createWalkingSequentialSelection(
                            routeContexts,
                            strategy,
                            validated.previouslyRecommendedPlaceIds(),
                            List.of(),
                            dailyOverlapLimit,
                            request.getResultId(),
                            hardExcludedPlaceIds,
                            reverseDateOrder
                    );
                } catch (IllegalArgumentException
                         | IllegalStateException exception) {
                    log.debug(
                            "도보 옵션 후보 분기 탐색 실패: resultId={}, "
                                    + "strategy={}, excluded={}, "
                                    + "reverseDateOrder={}, reason={}",
                            request.getResultId(),
                            strategy,
                            hardExcludedPlaceIds,
                            reverseDateOrder,
                            exception.getMessage()
                    );
                    continue;
                }
                if (selection == null) {
                    continue;
                }

                enqueueWalkingAlternativeExclusions(
                        pendingExclusions,
                        hardExcludedPlaceIds,
                        selection.placeCandidates()
                );

                try {
                    WalkingOptionCandidate candidate =
                            createWalkingOptionCandidate(
                                    request,
                                    validated,
                                    routeContexts,
                                    strategy,
                                    selection
                            );
                    uniqueCandidates.putIfAbsent(
                            candidate.recommendationKey(),
                            candidate
                    );
                } catch (IllegalArgumentException
                         | IllegalStateException exception) {
                    log.debug(
                            "도보 옵션 후보 일정 보정 실패: resultId={}, "
                                    + "strategy={}, excluded={}, "
                                    + "reverseDateOrder={}, reason={}",
                            request.getResultId(),
                            strategy,
                            hardExcludedPlaceIds,
                            reverseDateOrder,
                            exception.getMessage()
                    );
                }
            }
        }
        return List.copyOf(uniqueCandidates.values());
    }

    private Map<String, Integer> walkingCandidateCounts(
            Map<OptionStrategy, List<WalkingOptionCandidate>>
                    candidatesByStrategy
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OptionStrategy strategy : OptionStrategy.values()) {
            counts.put(
                    strategy.name(),
                    candidatesByStrategy.getOrDefault(
                            strategy,
                            List.of()
                    ).size()
            );
        }
        return Map.copyOf(counts);
    }

    private WalkingOptionCandidate createWalkingOptionCandidate(
            CourseRecommendRequest request,
            ValidatedRecommendation validated,
            Map<LocalDate, DailyRouteContext> routeContexts,
            OptionStrategy strategy,
            SequentialSelection selection
    ) {
        CourseOptimizeResponse estimated =
                courseOptimizationService.resolveFixedRouteEstimates(
                        CourseOptimizeRequest.builder()
                                .transportMode(TransportMode.WALKING)
                                .placeCandidates(
                                        selection.placeCandidates()
                                )
                                .build()
                );
        estimated = applyNaturalScheduleFlow(
                estimated,
                request.getDailyStartTime(),
                validated.scheduleType(),
                routeContexts,
                Map.of()
        );

        SequentialSelection orderedSelection =
                sequentialSelectionFromOptimized(estimated);
        String recommendationKey =
                createOrdinaryOptimizedCompositionSignature(
                        estimated.getOptimizedPlaces(),
                        TransportMode.WALKING
                );
        if (isExcludedRecommendationComposition(
                recommendationKey,
                validated.excludedRecommendationKeys(),
                validated.hotelCandidates()
        )) {
            CourseOptimizeResponse diversified =
                    createWalkingPreviousRecommendationFallback(
                            request,
                            validated,
                            routeContexts,
                            strategy,
                            estimated
                    );
            if (diversified == null) {
                throw new CourseRecommendationUnavailableException(
                        "이전 추천과 완전히 같은 도보 코스이며, 최소 3곳을 "
                                + "유지한 새 DAY 조합도 만들 수 없습니다. "
                                + "recommendationKey=" + recommendationKey
                );
            }
            estimated = diversified;
            orderedSelection = sequentialSelectionFromOptimized(estimated);
            recommendationKey =
                    createOrdinaryOptimizedCompositionSignature(
                            estimated.getOptimizedPlaces(),
                            TransportMode.WALKING
                    );
            log.warn(
                    "이전 추천과 동일한 도보 구성 대신 각 DAY의 일반 장소를 "
                            + "최소 한 곳씩 변경한 후보를 사용합니다: "
                            + "resultId={}, strategy={}, recommendationKey={}",
                    request.getResultId(),
                    strategy,
                    recommendationKey
            );
        }
        Map<LocalDate, Set<Long>> ordinaryPlaces =
                ordinaryPlaceIdsByDate(estimated);
        Set<Long> ordinaryPlaceIds = ordinaryPlaces.values().stream()
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new
                ));
        int previousRecommendationCount =
                (int) ordinaryPlaceIds.stream()
                        .filter(validated
                                .previouslyRecommendedPlaceIds()
                                ::contains)
                        .count();

        return new WalkingOptionCandidate(
                strategy,
                orderedSelection,
                estimated,
                recommendationKey,
                ordinaryPlaces,
                firstOrdinaryPlaceIdsByDate(estimated),
                resolveCourseRegion(estimated.getOptimizedPlaces()),
                previousRecommendationCount,
                calculatePlaceCountShortfall(
                        ordinaryPlaces,
                        routeContexts
                ),
                countRelaxedWalkingTierDays(estimated),
                averageRecommendationScore(
                        estimated.getOptimizedPlaces()
                )
        );
    }

    /**
     * 다시 추천 시 현재 후보가 이전 코스와 완전히 같으면 그대로 재사용하지 않는다.
     * 대신 장소가 4곳 이상인 모든 DAY에서 한 곳씩 제거한 조합을 만든다.
     *
     * <p>모든 DAY를 함께 변경하므로 1코스 DAY1과 2코스 DAY2처럼 날짜만
     * 바뀐 동일 DAY가 다시 생기는 것을 피할 수 있다. 식당·카페·관광지는
     * 가능한 한 한 곳 이상 남기고, 하루 최소 3곳과 도보 시간 상한은 유지한다.</p>
     */
    private CourseOptimizeResponse createWalkingPreviousRecommendationFallback(
            CourseRecommendRequest request,
            ValidatedRecommendation validated,
            Map<LocalDate, DailyRouteContext> routeContexts,
            OptionStrategy strategy,
            CourseOptimizeResponse estimated
    ) {
        Map<LocalDate, List<OptimizedPlaceDto>> ordinaryByDate =
                estimated.getOptimizedPlaces().stream()
                        .filter(place -> !"HOTEL".equalsIgnoreCase(
                                place.getCategory()
                        ))
                        .sorted(Comparator
                                .comparing(OptimizedPlaceDto::getVisitDate)
                                .thenComparing(
                                        OptimizedPlaceDto::getVisitOrder
                                ))
                        .collect(java.util.stream.Collectors.groupingBy(
                                OptimizedPlaceDto::getVisitDate,
                                TreeMap::new,
                                java.util.stream.Collectors.toList()
                        ));
        if (ordinaryByDate.isEmpty()
                || ordinaryByDate.values().stream()
                .anyMatch(places -> places.size() <= MIN_PLACES_PER_DAY)) {
            return null;
        }

        Map<LocalDate, List<Long>> removableIdsByDate = new TreeMap<>();
        for (Map.Entry<LocalDate, List<OptimizedPlaceDto>> entry
                : ordinaryByDate.entrySet()) {
            List<Long> removableIds = preferredWalkingNoveltyRemovalIds(
                    entry.getValue()
            );
            if (removableIds.isEmpty()) {
                return null;
            }
            removableIdsByDate.put(entry.getKey(), removableIds);
        }

        List<Map<LocalDate, Long>> removalPatterns =
                createWalkingNoveltyRemovalPatterns(
                        removableIdsByDate,
                        strategy
                );
        for (Map<LocalDate, Long> removalPattern : removalPatterns) {
            List<PlaceCandidateDto> reducedCandidates =
                    estimated.getOptimizedPlaces().stream()
                            .filter(place -> {
                                Long removedPlaceId = removalPattern.get(
                                        place.getVisitDate()
                                );
                                return removedPlaceId == null
                                        || !removedPlaceId.equals(
                                        place.getPlaceId()
                                );
                            })
                            .filter(place -> !"HOTEL".equalsIgnoreCase(
                                    place.getCategory()
                            ))
                            .map(this::toRouteCandidate)
                            .toList();
            CourseOptimizeResponse diversified;
            try {
                diversified = courseOptimizationService
                        .resolveFixedRouteEstimates(
                                CourseOptimizeRequest.builder()
                                        .transportMode(TransportMode.WALKING)
                                        .placeCandidates(reducedCandidates)
                                        .build()
                        );
                diversified = applyNaturalScheduleFlow(
                        diversified,
                        request.getDailyStartTime(),
                        validated.scheduleType(),
                        routeContexts,
                        Map.of()
                );
                validateWalkingDailyTimeLimits(
                        diversified,
                        request.getResultId(),
                        strategy,
                        "이전 추천 중복 회피 후보"
                );
            } catch (IllegalArgumentException | IllegalStateException exception) {
                continue;
            }

            Map<LocalDate, Set<Long>> ordinaryPlaces =
                    ordinaryPlaceIdsByDate(diversified);
            if (ordinaryPlaces.size() != ordinaryByDate.size()
                    || ordinaryPlaces.values().stream()
                    .anyMatch(placeIds -> placeIds.size()
                            < MIN_PLACES_PER_DAY)) {
                continue;
            }
            String diversifiedKey =
                    createOrdinaryOptimizedCompositionSignature(
                            diversified.getOptimizedPlaces(),
                            TransportMode.WALKING
                    );
            if (!isExcludedRecommendationComposition(
                    diversifiedKey,
                    validated.excludedRecommendationKeys(),
                    validated.hotelCandidates()
            )) {
                return diversified;
            }
        }
        return null;
    }

    /** 식당·카페·관광지를 한 곳 이상 남길 수 있는 장소를 우선 제거한다. */
    private List<Long> preferredWalkingNoveltyRemovalIds(
            List<OptimizedPlaceDto> dailyPlaces
    ) {
        Map<String, Long> categoryCounts = dailyPlaces.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        place -> normalizeCategory(place.getCategory()),
                        java.util.stream.Collectors.counting()
                ));
        Comparator<OptimizedPlaceDto> scoreComparator = Comparator
                .comparing(
                        OptimizedPlaceDto::getRecommendationScore,
                        Comparator.nullsFirst(Double::compareTo)
                )
                .thenComparing(OptimizedPlaceDto::getPlaceId);
        List<OptimizedPlaceDto> preferred = dailyPlaces.stream()
                .filter(place -> categoryCounts.getOrDefault(
                        normalizeCategory(place.getCategory()),
                        0L
                ) > 1L)
                .sorted(scoreComparator)
                .toList();
        Set<Long> preferredIds = preferred.stream()
                .map(OptimizedPlaceDto::getPlaceId)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new
                ));
        List<OptimizedPlaceDto> lastResort = dailyPlaces.stream()
                .filter(place -> !preferredIds.contains(place.getPlaceId()))
                .sorted(Comparator
                        .comparingInt((OptimizedPlaceDto place) -> {
                            String category = normalizeCategory(
                                    place.getCategory()
                            );
                            if ("TOUR".equals(category)) {
                                return 0;
                            }
                            if ("CAFE".equals(category)) {
                                return 1;
                            }
                            if ("RESTAURANT".equals(category)) {
                                return 2;
                            }
                            return 3;
                        })
                        .thenComparing(scoreComparator))
                .toList();
        List<Long> orderedIds = new ArrayList<>();
        preferred.forEach(place -> orderedIds.add(place.getPlaceId()));
        lastResort.forEach(place -> orderedIds.add(place.getPlaceId()));
        return orderedIds.stream().distinct().toList();
    }

    /**
     * 전략별로 같은 순번의 제거 후보를 사용해 모든 DAY를 동시에 바꾼다.
     * 우선 대각선 패턴을 만들고, 이전 추천과 다시 충돌할 때를 위해 순번을 회전한다.
     */
    private List<Map<LocalDate, Long>> createWalkingNoveltyRemovalPatterns(
            Map<LocalDate, List<Long>> removableIdsByDate,
            OptionStrategy strategy
    ) {
        int maximumChoices = removableIdsByDate.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);
        Map<String, Map<LocalDate, Long>> uniquePatterns =
                new LinkedHashMap<>();

        // 다일 일정은 날짜마다 한 전략만 원래 4곳 구성을 유지하고,
        // 나머지 두 전략은 서로 다른 우선 제거 장소를 사용한다.
        // 이렇게 하면 식당·카페를 보존하면서도 세 옵션의 같은 DAY가
        // 완전히 동일해지는 것을 막을 수 있다.
        Map<LocalDate, Long> primaryPattern = new TreeMap<>();
        int dateIndex = 0;
        for (Map.Entry<LocalDate, List<Long>> entry
                : removableIdsByDate.entrySet()) {
            List<Long> choices = entry.getValue();
            boolean keepOriginalDay = removableIdsByDate.size() > 1
                    && strategy.ordinal() == dateIndex % 3;
            if (!keepOriginalDay) {
                int leaveStrategy = dateIndex % 3;
                int removalOrder = strategy.ordinal() < leaveStrategy
                        ? strategy.ordinal()
                        : strategy.ordinal() - 1;
                int index = Math.floorMod(removalOrder, choices.size());
                primaryPattern.put(entry.getKey(), choices.get(index));
            }
            dateIndex++;
        }
        addWalkingNoveltyPattern(uniquePatterns, primaryPattern);

        // 이전 추천 목록에 위 조합까지 들어 있는 경우에는 모든 DAY를
        // 함께 변경하는 회전 패턴으로 다음 새 구성을 찾는다.
        int attempts = Math.max(maximumChoices, 1) * 3;
        for (int offset = 0; offset < attempts; offset++) {
            Map<LocalDate, Long> pattern = new TreeMap<>();
            for (Map.Entry<LocalDate, List<Long>> entry
                    : removableIdsByDate.entrySet()) {
                List<Long> choices = entry.getValue();
                int index = Math.floorMod(
                        strategy.ordinal() + offset,
                        choices.size()
                );
                pattern.put(entry.getKey(), choices.get(index));
            }
            addWalkingNoveltyPattern(uniquePatterns, pattern);
        }
        return List.copyOf(uniquePatterns.values());
    }

    private void addWalkingNoveltyPattern(
            Map<String, Map<LocalDate, Long>> uniquePatterns,
            Map<LocalDate, Long> pattern
    ) {
        if (pattern.isEmpty()) {
            return;
        }
        String signature = pattern.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        uniquePatterns.putIfAbsent(signature, Map.copyOf(pattern));
    }

    private void enqueueWalkingAlternativeExclusions(
            Deque<Set<Long>> pendingExclusions,
            Set<Long> currentExclusions,
            List<PlaceCandidateDto> selectedCandidates
    ) {
        if (currentExclusions.size() >= MAX_WALKING_EXCLUSION_DEPTH) {
            return;
        }
        List<Long> selectedPlaceIds = selectedCandidates.stream()
                .map(PlaceCandidateDto::getPlaceId)
                .distinct()
                .sorted()
                .toList();

        Map<LocalDate, Set<Long>> placeIdsByDate = new TreeMap<>();
        for (PlaceCandidateDto candidate : selectedCandidates) {
            placeIdsByDate.computeIfAbsent(
                    candidate.getVisitDate(),
                    ignored -> new LinkedHashSet<>()
            ).add(candidate.getPlaceId());
        }
        for (Set<Long> dailyPlaceIds : placeIdsByDate.values()) {
            Set<Long> next = new LinkedHashSet<>(currentExclusions);
            next.addAll(dailyPlaceIds);
            if (next.size() > currentExclusions.size()
                    && next.size() <= MAX_WALKING_EXCLUSION_DEPTH) {
                pendingExclusions.addLast(Set.copyOf(next));
            }
        }

        Set<Long> diversityExclusions = new LinkedHashSet<>(
                currentExclusions
        );
        diversityExclusions.addAll(selectedPlaceIds);
        if (diversityExclusions.size() > currentExclusions.size()
                && diversityExclusions.size()
                <= MAX_WALKING_EXCLUSION_DEPTH) {
            // 후보가 충분하면 현재 코스 전체를 비운 완전 독립 경로를 먼저 찾는다.
            pendingExclusions.addFirst(Set.copyOf(diversityExclusions));
        }

        selectedPlaceIds.forEach(placeId -> {
                    if (currentExclusions.contains(placeId)) {
                        return;
                    }
                    Set<Long> next = new LinkedHashSet<>(
                            currentExclusions
                    );
                    next.add(placeId);
                    if (next.size() <= MAX_WALKING_EXCLUSION_DEPTH) {
                        pendingExclusions.addLast(Set.copyOf(next));
                    }
                });
    }

    private SequentialSelection sequentialSelectionFromOptimized(
            CourseOptimizeResponse optimized
    ) {
        List<OptimizedPlaceDto> orderedPlaces =
                optimized.getOptimizedPlaces().stream()
                        .filter(place -> !"HOTEL".equalsIgnoreCase(
                                place.getCategory()
                        ))
                        .sorted(Comparator
                                .comparing(OptimizedPlaceDto::getVisitDate)
                                .thenComparing(
                                        OptimizedPlaceDto::getVisitOrder
                                ))
                        .toList();
        List<PlaceCandidateDto> candidates = orderedPlaces.stream()
                .map(this::toRouteCandidate)
                .toList();
        Map<LocalDate, Long> firstPlaceIds = new TreeMap<>();
        for (OptimizedPlaceDto place : orderedPlaces) {
            firstPlaceIds.putIfAbsent(
                    place.getVisitDate(),
                    place.getPlaceId()
            );
        }
        return new SequentialSelection(
                List.copyOf(candidates),
                Map.copyOf(firstPlaceIds)
        );
    }

    private Map<LocalDate, Long> firstOrdinaryPlaceIdsByDate(
            CourseOptimizeResponse optimized
    ) {
        Map<LocalDate, Long> firstPlaceIds = new TreeMap<>();
        optimized.getOptimizedPlaces().stream()
                .filter(place -> !"HOTEL".equalsIgnoreCase(
                        place.getCategory()
                ))
                .sorted(Comparator
                        .comparing(OptimizedPlaceDto::getVisitDate)
                        .thenComparing(OptimizedPlaceDto::getVisitOrder))
                .forEach(place -> firstPlaceIds.putIfAbsent(
                        place.getVisitDate(),
                        place.getPlaceId()
                ));
        return Map.copyOf(firstPlaceIds);
    }

    private int calculatePlaceCountShortfall(
            Map<LocalDate, Set<Long>> ordinaryPlacesByDate,
            Map<LocalDate, DailyRouteContext> routeContexts
    ) {
        int shortfall = 0;
        for (Map.Entry<LocalDate, DailyRouteContext> entry
                : routeContexts.entrySet()) {
            int actual = ordinaryPlacesByDate.getOrDefault(
                    entry.getKey(),
                    Set.of()
            ).size();
            shortfall += Math.max(
                    0,
                    entry.getValue().plan().targetPlaceCount() - actual
            );
        }
        return shortfall;
    }

    private double averageRecommendationScore(
            List<OptimizedPlaceDto> places
    ) {
        return places.stream()
                .filter(place -> !"HOTEL".equalsIgnoreCase(
                        place.getCategory()
                ))
                .map(OptimizedPlaceDto::getRecommendationScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private int countRelaxedWalkingTierDays(
            CourseOptimizeResponse optimized
    ) {
        Map<LocalDate, List<OptimizedPlaceDto>> placesByDate =
                optimized.getOptimizedPlaces().stream()
                        .filter(place -> !"HOTEL".equalsIgnoreCase(
                                place.getCategory()
                        ))
                        .collect(java.util.stream.Collectors.groupingBy(
                                OptimizedPlaceDto::getVisitDate,
                                TreeMap::new,
                                java.util.stream.Collectors.toList()
                        ));
        int relaxedDays = 0;
        for (List<OptimizedPlaceDto> dailyPlaces
                : placesByDate.values()) {
            int legCount = Math.max(0, dailyPlaces.size() - 1);
            if (legCount == 0) {
                continue;
            }
            double totalMinutes = dailyPlaces.stream()
                    .mapToDouble(place -> valueOrZero(
                            place.getTravelTimeFromPreviousMinutes()
                    ))
                    .sum();
            if (totalMinutes / legCount
                    > WALKING_TARGET_AVERAGE_MINUTES + EPSILON) {
                relaxedDays++;
            }
        }
        return relaxedDays;
    }

    private WalkingCombinationSelection selectWalkingCandidateCombinations(
            Map<OptionStrategy, List<WalkingOptionCandidate>>
                    candidatesByStrategy,
            int dailyOverlapLimit
    ) {
        return new WalkingCombinationSelection(
                createWalkingCandidateCombinations(
                        candidatesByStrategy,
                        dailyOverlapLimit
                ),
                dailyOverlapLimit
        );
    }

    /**
     * 서로 다른 세 조합이 부족하면 도보 시간 제한은 그대로 둔 채 옵션 간 장소
     * 중복 수만 단계적으로 완화한다. 날짜가 같거나 다른 모든 DAY 조합에 동일하게
     * 적용하며, 숙소를 제외한 하루 장소 구성이 완전히 같은 조합은 끝까지 허용하지
     * 않는다.
     */
    private WalkingCombinationSelection findRelaxedWalkingCombinationSelection(
            Map<OptionStrategy, List<WalkingOptionCandidate>>
                    candidatesByStrategy,
            int originalDailyOverlapLimit,
            Long resultId
    ) {
        for (int relaxedOverlap = originalDailyOverlapLimit + 1;
             relaxedOverlap <= MAX_WALKING_RELAXED_DAILY_OVERLAP_LIMIT;
             relaxedOverlap++) {
            WalkingCombinationSelection selection =
                    selectWalkingCandidateCombinations(
                            candidatesByStrategy,
                            relaxedOverlap
                    );
            if (!selection.combinations().isEmpty()) {
                log.warn(
                        "도보 시간 제한은 유지하고 옵션 간 전체 DAY 조합의 "
                                + "중복 상한만 완화했습니다: resultId={}, "
                                + "overlapLimit={}, "
                                + "candidateCounts={}",
                        resultId,
                        relaxedOverlap,
                        walkingCandidateCounts(candidatesByStrategy)
                );
                return selection;
            }
        }

        return new WalkingCombinationSelection(
                List.of(),
                originalDailyOverlapLimit
        );
    }

    private List<WalkingCandidateCombination>
    createWalkingCandidateCombinations(
            Map<OptionStrategy, List<WalkingOptionCandidate>>
                    candidatesByStrategy,
            int dailyOverlapLimit
    ) {
        List<WalkingCandidateCombination> strategyCombinations =
                new ArrayList<>();
        for (WalkingOptionCandidate preference
                : candidatesByStrategy.getOrDefault(
                        OptionStrategy.PREFERENCE,
                        List.of()
                )) {
            for (WalkingOptionCandidate minimumDistance
                    : candidatesByStrategy.getOrDefault(
                            OptionStrategy.MIN_DISTANCE,
                            List.of()
                    )) {
                for (WalkingOptionCandidate balanced
                        : candidatesByStrategy.getOrDefault(
                                OptionStrategy.BALANCED,
                                List.of()
                        )) {
                    WalkingCandidateCombination combination =
                            evaluateWalkingCandidateCombination(
                                    List.of(
                                            preference,
                                            minimumDistance,
                                            balanced
                                    ),
                                    dailyOverlapLimit
                            );
                    if (combination != null) {
                        strategyCombinations.add(combination);
                    }
                }
            }
        }

        Map<String, WalkingOptionCandidate> allUniqueCandidates =
                new LinkedHashMap<>();
        for (OptionStrategy strategy : OptionStrategy.values()) {
            for (WalkingOptionCandidate candidate
                    : candidatesByStrategy.getOrDefault(
                            strategy,
                            List.of()
                    )) {
                allUniqueCandidates.putIfAbsent(
                        candidate.recommendationKey(),
                        candidate
                );
            }
        }
        List<WalkingOptionCandidate> allCandidates =
                new ArrayList<>(allUniqueCandidates.values());
        List<WalkingCandidateCombination> fallbackCombinations =
                new ArrayList<>();
        for (int first = 0; first < allCandidates.size(); first++) {
            for (int second = first + 1;
                    second < allCandidates.size();
                    second++) {
                for (int third = second + 1;
                        third < allCandidates.size();
                        third++) {
                    WalkingCandidateCombination combination =
                            evaluateWalkingCandidateCombination(
                                    List.of(
                                            allCandidates.get(first),
                                            allCandidates.get(second),
                                            allCandidates.get(third)
                                    ),
                                    dailyOverlapLimit
                            );
                    if (combination != null
                            && combination.strategyCoveragePenalty() > 0) {
                        fallbackCombinations.add(combination);
                    }
                }
            }
        }

        Comparator<WalkingCandidateCombination> comparator =
                walkingCombinationComparator();
        strategyCombinations.sort(comparator);
        fallbackCombinations.sort(comparator);

        Map<String, WalkingCandidateCombination> selected =
                new LinkedHashMap<>();
        int strategyLimit = Math.min(
                512,
                MAX_WALKING_JOINT_COMBINATIONS
        );
        strategyCombinations.stream()
                .limit(strategyLimit)
                .forEach(combination -> selected.putIfAbsent(
                        combination.signature(),
                        combination
                ));
        fallbackCombinations.stream()
                .limit(MAX_WALKING_JOINT_COMBINATIONS - selected.size())
                .forEach(combination -> selected.putIfAbsent(
                        combination.signature(),
                        combination
                ));
        return List.copyOf(selected.values());
    }

    private WalkingCandidateCombination evaluateWalkingCandidateCombination(
            List<WalkingOptionCandidate> candidates,
            int dailyOverlapLimit
    ) {
        if (candidates.size() != 3) {
            return null;
        }
        if (candidates.stream()
                .map(WalkingOptionCandidate::recommendationKey)
                .distinct()
                .count() != 3) {
            return null;
        }

        int totalOverlap = 0;
        int firstPlaceReuse = 0;
        int regionReuse = 0;
        for (int leftIndex = 0;
                leftIndex < candidates.size();
                leftIndex++) {
            WalkingOptionCandidate left = candidates.get(leftIndex);
            for (int rightIndex = leftIndex + 1;
                    rightIndex < candidates.size();
                    rightIndex++) {
                WalkingOptionCandidate right = candidates.get(rightIndex);
                for (Map.Entry<LocalDate, Set<Long>> leftDay
                        : left.ordinaryPlacesByDate().entrySet()) {
                    for (Map.Entry<LocalDate, Set<Long>> rightDay
                            : right.ordinaryPlacesByDate().entrySet()) {
                        if (sameOrdinaryDayComposition(
                                leftDay.getValue(),
                                rightDay.getValue()
                        )) {
                            return null;
                        }
                        Set<Long> overlap = intersectPlaceIds(
                                leftDay.getValue(),
                                rightDay.getValue()
                        );
                        if (overlap.size() > dailyOverlapLimit) {
                            return null;
                        }
                        totalOverlap += overlap.size();

                        Long leftFirst = left.firstPlaceIdsByDate().get(
                                leftDay.getKey()
                        );
                        Long rightFirst = right.firstPlaceIdsByDate().get(
                                rightDay.getKey()
                        );
                        if (leftFirst != null
                                && leftFirst.equals(rightFirst)) {
                            firstPlaceReuse++;
                        }
                    }
                }
                if (left.region() != null
                        && left.region().equals(right.region())) {
                    regionReuse++;
                }
            }
        }

        int strategyCoveragePenalty = 3 - (int) candidates.stream()
                .map(WalkingOptionCandidate::strategy)
                .distinct()
                .count();
        int placeCountShortfall = candidates.stream()
                .mapToInt(WalkingOptionCandidate::placeCountShortfall)
                .sum();
        int relaxedTierDayCount = candidates.stream()
                .mapToInt(WalkingOptionCandidate::relaxedTierDayCount)
                .sum();
        int previousRecommendationCount = candidates.stream()
                .mapToInt(
                        WalkingOptionCandidate::previousRecommendationCount
                )
                .sum();
        double objectivePenalty = candidates.stream()
                .mapToDouble(candidate -> switch (candidate.strategy()) {
                    case PREFERENCE ->
                            -candidate.averageRecommendationScore();
                    case MIN_DISTANCE ->
                            valueOrZero(
                                    candidate.estimated().getTotalTravelTimeMinutes()
                            ) * 5.0
                                    + valueOrZero(
                                    candidate.estimated().getTotalDistanceKm()
                            );
                    case BALANCED ->
                            valueOrZero(
                                    candidate.estimated().getTotalTravelTimeMinutes()
                            )
                                    - candidate.averageRecommendationScore();
                })
                .sum();
        String signature = candidates.stream()
                .map(candidate -> candidate.strategy().name()
                        + "=" + candidate.recommendationKey())
                .sorted()
                .reduce((left, right) -> left + "||" + right)
                .orElse("");
        return new WalkingCandidateCombination(
                List.copyOf(candidates),
                strategyCoveragePenalty,
                placeCountShortfall,
                relaxedTierDayCount,
                totalOverlap,
                previousRecommendationCount,
                regionReuse,
                firstPlaceReuse,
                objectivePenalty,
                signature
        );
    }

    private Comparator<WalkingCandidateCombination>
    walkingCombinationComparator() {
        return Comparator
                .comparingInt(
                        WalkingCandidateCombination::strategyCoveragePenalty
                )
                .thenComparingInt(
                        WalkingCandidateCombination::placeCountShortfall
                )
                .thenComparingInt(
                        WalkingCandidateCombination::relaxedTierDayCount
                )
                .thenComparingInt(
                        WalkingCandidateCombination::totalOverlap
                )
                .thenComparingInt(
                        WalkingCandidateCombination::previousRecommendationCount
                )
                .thenComparingInt(
                        WalkingCandidateCombination::regionReuse
                )
                .thenComparingInt(
                        WalkingCandidateCombination::firstPlaceReuse
                )
                .thenComparingDouble(
                        WalkingCandidateCombination::objectivePenalty
                )
                .thenComparing(WalkingCandidateCombination::signature);
    }

    private CourseOptimizeResponse resolveActualWalkingCandidate(
            WalkingOptionCandidate candidate,
            Long resultId,
            Map<String, CourseOptimizeResponse> actualRouteCache,
            Set<String> invalidActualRouteKeys,
            Set<LocalDate> actualLimitFailureDates
    ) {
        String key = candidate.recommendationKey();
        if (invalidActualRouteKeys.contains(key)) {
            return null;
        }
        CourseOptimizeResponse cached = actualRouteCache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            CourseOptimizeResponse actual = optimizeFixedWalkingSelection(
                    candidate.orderedSelection(),
                    candidate.strategy(),
                    resultId
            );
            actualRouteCache.put(key, actual);
            return actual;
        } catch (ActualWalkingLimitException exception) {
            invalidActualRouteKeys.add(key);
            actualLimitFailureDates.addAll(
                    exception.violationDates()
            );
            log.warn(
                    "추정 경로는 통과했지만 실제 도보 제한에서 탈락해 다음 "
                            + "세 코스 조합을 확인합니다: resultId={}, "
                            + "strategy={}, recommendationKey={}, "
                            + "violationDates={}, reason={}",
                    resultId,
                    candidate.strategy(),
                    key,
                    exception.violationDates(),
                    exception.getMessage()
            );
            return null;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            invalidActualRouteKeys.add(key);
            log.warn(
                    "추정 경로는 통과했지만 실제 도보 제한에서 탈락해 다음 "
                            + "세 코스 조합을 확인합니다: resultId={}, "
                            + "strategy={}, recommendationKey={}, reason={}",
                    resultId,
                    candidate.strategy(),
                    key,
                    exception.getMessage()
            );
            return null;
        }
    }

    private CourseRecommendResponse buildWalkingJointResponse(
            CourseRecommendRequest request,
            ValidatedRecommendation validated,
            Map<LocalDate, DailyRouteContext> routeContexts,
            int dailyOverlapLimit,
            List<ResolvedWalkingCandidate> resolvedCandidates,
            Map<LocalDate, Integer> originalRequestedPlaceCounts
    ) {
        List<CourseOptionResponse> courseOptions = new ArrayList<>();
        List<Map<LocalDate, Set<Long>>> generatedOptionPlaces =
                new ArrayList<>();
        Set<String> generatedRecommendationKeys = new LinkedHashSet<>();
        Set<Long> usedHotelIds = new LinkedHashSet<>();
        int optionNo = 1;

        for (ResolvedWalkingCandidate resolved : resolvedCandidates) {
            WalkingOptionCandidate candidate = resolved.candidate();
            CourseOptimizeResponse optimized = resolved.actual();
            Long selectedHotelId = null;

            if (validated.dailyPlans().size() >= 2) {
                HotelEvaluation selectedHotelEvaluation =
                        selectHotelCandidateForOption(
                                validated.hotelCandidates(),
                                optimized,
                                candidate.strategy(),
                                validated.previouslyRecommendedPlaceIds(),
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
                    if (!selectedHotelEvaluation.estimated()) {
                        optimized = applyActualWalkingHotelLegs(
                                optimized,
                                selectedHotel
                        );
                    }
                    selectedHotelId = selectedHotel.getPlaceId();
                    usedHotelIds.add(selectedHotelId);
                }
            }

            try {
                validateWalkingDailyTimeLimits(
                        optimized,
                        request.getResultId(),
                        candidate.strategy(),
                        "세 코스 동시 선택·숙소 반영"
                );
            } catch (IllegalStateException exception) {
                log.warn(
                        "선택 조합의 최종 숙소 반영 검증에 실패해 다음 조합을 "
                                + "확인합니다: resultId={}, strategy={}, reason={}",
                        request.getResultId(),
                        candidate.strategy(),
                        exception.getMessage()
                );
                return null;
            }

            Map<LocalDate, Set<Long>> ordinaryPlaces =
                    ordinaryPlaceIdsByDate(optimized);
            if (findFinalOverlapViolation(
                    ordinaryPlaces,
                    generatedOptionPlaces,
                    dailyOverlapLimit
            ) != null) {
                return null;
            }

            String ordinaryRecommendationKey =
                    createOrdinaryOptimizedCompositionSignature(
                            optimized.getOptimizedPlaces(),
                            TransportMode.WALKING
                    );
            if (isExcludedRecommendationComposition(
                    ordinaryRecommendationKey,
                    validated.excludedRecommendationKeys(),
                    validated.hotelCandidates()
            ) || !generatedRecommendationKeys.add(
                    ordinaryRecommendationKey
            )) {
                return null;
            }

            // 숙소는 코스 중복 여부와 무관하므로 응답 키에도 포함하지 않는다.
            String recommendationKey = ordinaryRecommendationKey;
            generatedOptionPlaces.add(ordinaryPlaces);
            logOverlapResult(
                    request.getResultId(),
                    candidate.strategy(),
                    ordinaryPlaces,
                    generatedOptionPlaces.subList(
                            0,
                            generatedOptionPlaces.size() - 1
                    ),
                    dailyOverlapLimit
            );
            courseOptions.add(toOptionResponse(
                    optionNo++,
                    candidate.strategy(),
                    optimized,
                    request.getDailyStartTime(),
                    recommendationKey,
                    validated.dailyPlans().size() >= 2,
                    selectedHotelId != null,
                    originalRequestedPlaceCounts
            ));
        }

        if (courseOptions.size() != 3) {
            return null;
        }
        normalizeFinalOptionRoles(courseOptions);
        return CourseRecommendResponse.builder()
                .resultId(request.getResultId())
                .travelCode(request.getTravelCode())
                .transportMode(TransportMode.WALKING)
                .preferredRegions(validated.preferredRegions())
                .estimatedTravelTimes(courseOptions.stream()
                        .anyMatch(option -> Boolean.TRUE.equals(
                                option.getEstimatedTravelTimes()
                        )))
                .dailyStartTime(request.getDailyStartTime())
                .optionCount(courseOptions.size())
                .courseOptions(courseOptions)
                .build();
    }

    private String placeIdSetSignature(Set<Long> placeIds) {
        return placeIds.stream()
                .sorted()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("-");
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
        String scheduleType = resolveScheduleType(
                request.getTravelCode()
        );
        List<String> preferredRegions = normalizePreferredRegions(
                request.getPreferredRegions()
        );

        Map<LocalDate, ValidatedDailyPlan> plansByDate = new TreeMap<>();
        for (DailyPlanRequest dailyPlan : request.getDailyPlans()) {
            ValidatedDailyPlan validatedPlan = validateDailyPlan(
                    dailyPlan,
                    scheduleType
            );
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
        applyRecommendationScoreAdjustments(
                dailyPlans,
                hotelCandidates,
                combinedPreviousPlaceIds,
                preferredRegions
        );

        return new ValidatedRecommendation(
                dailyPlans,
                excludedRecommendationKeys,
                request.getTransportMode(),
                Set.copyOf(combinedPreviousPlaceIds),
                hotelCandidates,
                scheduleType,
                preferredRegions
        );
    }

    private String resolveScheduleType(String travelCode) {
        if (travelCode == null || travelCode.isBlank()) {
            return null;
        }
        String normalized = travelCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[AH][TM][LB][SD][PR]")) {
            throw new IllegalArgumentException(
                    "여행 유형 코드는 A/H, T/M, L/B, S/D, P/R 순서여야 합니다."
            );
        }
        return normalized.substring(normalized.length() - 1);
    }

    private List<String> normalizePreferredRegions(
            List<String> source
    ) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> regions = new LinkedHashSet<>();
        for (String value : source) {
            String region = normalizeDistrict(value);
            if (region != null) {
                regions.add(region);
            }
            if (regions.size() >= MAX_PREFERRED_REGIONS) {
                break;
            }
        }
        return List.copyOf(regions);
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
     * 상위 구의 순위별 가산점과 재추천 감점·신규 가산점을 한 번에 반영한 뒤,
     * 화면 점수를 70~95 범위로 다시 보정한다.
     */
    private void applyRecommendationScoreAdjustments(
            List<ValidatedDailyPlan> dailyPlans,
            List<PlaceCandidateDto> hotelCandidates,
            Set<Long> previouslyRecommendedPlaceIds,
            List<String> preferredRegions
    ) {
        if (previouslyRecommendedPlaceIds.isEmpty()
                && preferredRegions.isEmpty()) {
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
                .mapToDouble(candidate -> adjustedRecommendationScore(
                        candidate,
                        previouslyRecommendedPlaceIds,
                        preferredRegions
                ))
                .min()
                .orElse(0.0);
        double maximum = candidates.stream()
                .mapToDouble(candidate -> adjustedRecommendationScore(
                        candidate,
                        previouslyRecommendedPlaceIds,
                        preferredRegions
                ))
                .max()
                .orElse(0.0);

        for (PlaceCandidateDto candidate : candidates) {
            double adjusted = adjustedRecommendationScore(
                    candidate,
                    previouslyRecommendedPlaceIds,
                    preferredRegions
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

    private double adjustedRecommendationScore(
            PlaceCandidateDto candidate,
            Set<Long> previouslyRecommendedPlaceIds,
            List<String> preferredRegions
    ) {
        double score = valueOrZero(candidate.getRecommendationScore())
                + preferredRegionBonus(candidate, preferredRegions);
        if (previouslyRecommendedPlaceIds.isEmpty()) {
            return score;
        }
        return previouslyRecommendedPlaceIds.contains(candidate.getPlaceId())
                ? score - PREVIOUSLY_RECOMMENDED_PENALTY
                : score + NEW_PLACE_BONUS;
    }

    private double preferredRegionBonus(
            PlaceCandidateDto candidate,
            List<String> preferredRegions
    ) {
        String region = resolveCandidateDistrict(candidate);
        if (region == null) {
            return 0.0;
        }
        int index = preferredRegions.indexOf(region);
        return index >= 0 && index < PREFERRED_REGION_BONUSES.size()
                ? PREFERRED_REGION_BONUSES.get(index)
                : 0.0;
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
    private ValidatedDailyPlan validateDailyPlan(
            DailyPlanRequest dailyPlan,
            String scheduleType
    ) {
        if (dailyPlan == null) {
            throw new IllegalArgumentException("날짜별 일정은 null일 수 없습니다.");
        }
        LocalDate visitDate = dailyPlan.getVisitDate();
        if (visitDate == null) {
            throw new IllegalArgumentException("날짜별 일정의 방문 날짜는 필수입니다.");
        }
        Integer targetPlaceCount = dailyPlan.getTargetPlaceCount();
        if (targetPlaceCount == null
                || targetPlaceCount < MIN_PLACES_PER_DAY) {
            throw new IllegalArgumentException(
                    "하루 코스는 최소 " + MIN_PLACES_PER_DAY
                            + "곳 이상이어야 합니다. visitDate=" + visitDate
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
        Map<String, Integer> finalCategoryTargets =
                deriveFinalCategoryTargets(
                        categoryTargets,
                        targetPlaceCount,
                        scheduleType
                );

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
            int required = finalCategoryTargets.getOrDefault(category, 0);
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

        return new ValidatedDailyPlan(
                visitDate,
                targetPlaceCount,
                finalCategoryTargets,
                candidates
        );
    }

    /** 표준 하루 장소 수에서는 P형 3·2·1, R형 2·1·1을 강제로 사용한다. */
    private Map<String, Integer> deriveFinalCategoryTargets(
            Map<String, Integer> requestedTargets,
            int targetPlaceCount,
            String scheduleType
    ) {
        if (targetPlaceCount == 6
                && !"R".equals(scheduleType)) {
            return fixedCategoryTargets(3, 2, 1);
        }
        if (targetPlaceCount == 4
                && !"P".equals(scheduleType)) {
            return fixedCategoryTargets(2, 1, 1);
        }
        return deriveFinalCategoryTargets(
                requestedTargets,
                targetPlaceCount
        );
    }

    /**
     * 장소 수를 줄일 때 기존에 요청된 카테고리는 가능한 한 한 곳씩 남긴다.
     *
     * <p>P형 3·2·1은 5곳에서 2·2·1, 4곳에서 2·1·1,
     * 3곳에서 1·1·1, 실제 경로 최후 fallback인 2곳에서 1·1·0
     * 순서로 감소한다.</p>
     */
    private Map<String, Integer> deriveFinalCategoryTargets(
            Map<String, Integer> requestedTargets,
            int targetPlaceCount
    ) {
        int requestedTour = requestedTargets.getOrDefault("TOUR", 0);
        int requestedRestaurant =
                requestedTargets.getOrDefault("RESTAURANT", 0);
        int requestedCafe = requestedTargets.getOrDefault("CAFE", 0);
        if (requestedTour == 3
                && requestedRestaurant == 2
                && requestedCafe == 1) {
            return switch (targetPlaceCount) {
                case 6 -> fixedCategoryTargets(3, 2, 1);
                case 5 -> fixedCategoryTargets(2, 2, 1);
                case 4 -> fixedCategoryTargets(2, 1, 1);
                case 3 -> fixedCategoryTargets(1, 1, 1);
                case 2 -> fixedCategoryTargets(1, 1, 0);
                default -> throw new IllegalArgumentException(
                        "P형 하루 장소 수는 2~6곳이어야 합니다."
                );
            };
        }
        if (requestedTour == 2
                && requestedRestaurant == 2
                && requestedCafe == 1) {
            return switch (targetPlaceCount) {
                case 5 -> fixedCategoryTargets(2, 2, 1);
                case 4 -> fixedCategoryTargets(2, 1, 1);
                case 3 -> fixedCategoryTargets(1, 1, 1);
                case 2 -> fixedCategoryTargets(1, 1, 0);
                default -> throw new IllegalArgumentException(
                        "축소된 P형 하루 장소 수는 2~5곳이어야 합니다."
                );
            };
        }
        if (requestedTour == 2
                && requestedRestaurant == 1
                && requestedCafe == 1) {
            return switch (targetPlaceCount) {
                case 4 -> fixedCategoryTargets(2, 1, 1);
                case 3 -> fixedCategoryTargets(1, 1, 1);
                case 2 -> fixedCategoryTargets(1, 1, 0);
                default -> throw new IllegalArgumentException(
                        "R형 하루 장소 수는 2~4곳이어야 합니다."
                );
            };
        }
        if (requestedTour == 1
                && requestedRestaurant == 1
                && requestedCafe == 1) {
            return switch (targetPlaceCount) {
                case 3 -> fixedCategoryTargets(1, 1, 1);
                case 2 -> fixedCategoryTargets(1, 1, 0);
                default -> throw new IllegalArgumentException(
                        "축소된 일정의 하루 장소 수는 2~3곳이어야 합니다."
                );
            };
        }

        int requestedTargetSum = requestedTargets.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        if (requestedTargetSum < targetPlaceCount) {
            throw new IllegalArgumentException(
                    "카테고리 목표 합계가 선택할 장소 수보다 작습니다."
            );
        }
        Map<String, Integer> finalTargets = new LinkedHashMap<>();
        for (String category : SUPPORTED_CATEGORIES) {
            finalTargets.put(
                    category,
                    requestedTargets.getOrDefault(category, 0)
            );
        }

        int requestedCategoryCount = (int) finalTargets.values().stream()
                .filter(count -> count > 0)
                .count();
        int minimumPerRequestedCategory =
                targetPlaceCount >= requestedCategoryCount ? 1 : 0;
        List<String> reductionOrder = List.of(
                "TOUR",
                "RESTAURANT",
                "CAFE",
                "HOTEL"
        );
        int currentTotal = requestedTargetSum;
        while (currentTotal > targetPlaceCount) {
            String categoryToReduce = reductionOrder.stream()
                    .filter(category -> finalTargets.getOrDefault(
                            category,
                            0
                    ) > (requestedTargets.getOrDefault(category, 0) > 0
                            ? minimumPerRequestedCategory
                            : 0))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "카테고리를 유지한 채 장소 수를 줄일 수 없습니다."
                    ));
            finalTargets.put(
                    categoryToReduce,
                    finalTargets.get(categoryToReduce) - 1
            );
            currentTotal--;
        }
        return Map.copyOf(finalTargets);
    }

    private Map<String, Integer> fixedCategoryTargets(
            int tour,
            int restaurant,
            int cafe
    ) {
        Map<String, Integer> targets = new LinkedHashMap<>();
        targets.put("TOUR", tour);
        targets.put("RESTAURANT", restaurant);
        targets.put("CAFE", cafe);
        targets.put("HOTEL", 0);
        return Map.copyOf(targets);
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
     * <p>큰 후보 풀의 조합 탐색은 세 이동수단 모두 외부 호출 없는 추정 행렬을
     * 사용한다. 도보의 실제 20분 상한은 세 옵션 조합을 고른 뒤 최종 인접 구간만
     * 조회해 다시 검증한다.</p>
     */
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
                            ScoreRange.from(dailyPlan.placeCandidates()),
                            RouteCostRange.from(routeMatrix),
                            transportMode == TransportMode.WALKING
                                    ? createWalkingNeighborCandidates(
                                    dailyPlan.placeCandidates(),
                                    routeMatrix
                            )
                                    : Map.of()
                    )
            );
        }
        return contexts;
    }

    /**
     * 실제 ORS 제한을 넘긴 DAY만 목표 장소 수를 한 곳 낮춘다.
     * 기존 후보 행렬과 인접 목록은 그대로 재사용하므로 후보 풀 API를 다시 부르지 않는다.
     */
    private Map<LocalDate, DailyRouteContext>
    reduceWalkingRouteContextsAfterActualFailure(
            Map<LocalDate, DailyRouteContext> routeContexts,
            Set<LocalDate> failureDates,
            String scheduleType,
            Long resultId
    ) {
        if (failureDates.isEmpty()) {
            return routeContexts;
        }

        Map<LocalDate, DailyRouteContext> reducedContexts = new TreeMap<>();
        for (Map.Entry<LocalDate, DailyRouteContext> entry
                : routeContexts.entrySet()) {
            LocalDate visitDate = entry.getKey();
            DailyRouteContext context = entry.getValue();
            int currentTarget = context.plan().targetPlaceCount();
            if (!failureDates.contains(visitDate)
                    || currentTarget
                    <= MIN_ACTUAL_WALKING_REPAIR_PLACES_PER_DAY) {
                reducedContexts.put(visitDate, context);
                continue;
            }

            int reducedTarget = currentTarget - 1;
            Map<String, Integer> reducedCategoryTargets =
                    deriveWalkingFallbackCategoryTargets(
                            context.plan(),
                            reducedTarget,
                            scheduleType
                    );
            ValidatedDailyPlan reducedPlan = new ValidatedDailyPlan(
                    visitDate,
                    reducedTarget,
                    reducedCategoryTargets,
                    context.plan().placeCandidates()
            );
            reducedContexts.put(
                    visitDate,
                    new DailyRouteContext(
                            reducedPlan,
                            context.routeMatrix(),
                            context.candidateIndexes(),
                            context.scoreRange(),
                            context.routeCostRange(),
                            context.walkingNeighborCandidates()
                    )
            );
            log.warn(
                    "실제 ORS 도보 제한을 만족하는 세 코스 조합이 없어 "
                            + "문제 DAY의 장소 수를 한 곳 줄여 다시 선택합니다: "
                            + "resultId={}, visitDate={}, beforePlaces={}, "
                            + "afterPlaces={}, categoryTargets={}",
                    resultId,
                    visitDate,
                    currentTarget,
                    reducedTarget,
                    reducedCategoryTargets
            );
        }
        return reducedContexts;
    }

    /**
     * P/R 표준 카테고리 비율을 장소 수 축소 단계에도 유지한다.
     * 최후의 2곳 단계에서는 관광지와 식당을 남기고 카페를 먼저 제외한다.
     */
    private Map<String, Integer> deriveWalkingFallbackCategoryTargets(
            ValidatedDailyPlan plan,
            int targetPlaceCount,
            String scheduleType
    ) {
        Map<String, Integer> currentTargets = plan.categoryTargets();
        boolean hasTour = currentTargets.getOrDefault("TOUR", 0) > 0;
        boolean hasRestaurant =
                currentTargets.getOrDefault("RESTAURANT", 0) > 0;
        boolean hasCafe = currentTargets.getOrDefault("CAFE", 0) > 0;

        if (targetPlaceCount == 2) {
            if (hasTour && hasRestaurant) {
                return fixedCategoryTargets(1, 1, 0);
            }
            if (hasTour && hasCafe) {
                return fixedCategoryTargets(1, 0, 1);
            }
            if (hasRestaurant && hasCafe) {
                return fixedCategoryTargets(0, 1, 1);
            }
        }

        if (hasTour && hasRestaurant && hasCafe) {
            if ("P".equals(scheduleType)) {
                return switch (targetPlaceCount) {
                    case 6 -> fixedCategoryTargets(3, 2, 1);
                    case 5 -> fixedCategoryTargets(2, 2, 1);
                    case 4 -> fixedCategoryTargets(2, 1, 1);
                    case 3 -> fixedCategoryTargets(1, 1, 1);
                    default -> deriveFinalCategoryTargets(
                            currentTargets,
                            targetPlaceCount
                    );
                };
            }
            if ("R".equals(scheduleType)) {
                return switch (targetPlaceCount) {
                    case 4 -> fixedCategoryTargets(2, 1, 1);
                    case 3 -> fixedCategoryTargets(1, 1, 1);
                    default -> deriveFinalCategoryTargets(
                            currentTargets,
                            targetPlaceCount
                    );
                };
            }
        }
        return deriveFinalCategoryTargets(
                currentTargets,
                targetPlaceCount
        );
    }

    private String walkingTargetSignature(
            Map<LocalDate, DailyRouteContext> routeContexts
    ) {
        return routeContexts.entrySet().stream()
                .map(entry -> entry.getKey()
                        + "="
                        + entry.getValue().plan().targetPlaceCount())
                .reduce((left, right) -> left + "," + right)
                .orElse("-");
    }

    /**
     * 후보 풀의 도보 20분 이내 인접 목록을 날짜별로 한 번만 만든다.
     *
     * <p>기존에는 빔 탐색의 모든 상태에서 후보 전체를 다시 훑고 같은 행렬 값을
     * 비교했기 때문에 P형 6곳·다일 일정에서 계산량이 급증했다. 이후 탐색은 현재
     * 장소의 인접 목록만 확인하므로 후보 풀이 90개까지 늘어도 같은 비교를 반복하지
     * 않는다.</p>
     */
    private Map<Long, List<PlaceCandidateDto>>
    createWalkingNeighborCandidates(
            List<PlaceCandidateDto> candidates,
            RouteMatrix routeMatrix
    ) {
        Map<Long, List<PlaceCandidateDto>> neighbors =
                new LinkedHashMap<>();
        for (int fromIndex = 0;
             fromIndex < candidates.size();
             fromIndex++) {
            List<PlaceCandidateDto> reachable = new ArrayList<>();
            for (int toIndex = 0;
                 toIndex < candidates.size();
                 toIndex++) {
                double travelTimeMinutes =
                        routeMatrix.getTravelTimeMinutes(
                        fromIndex,
                        toIndex
                );
                if (fromIndex == toIndex
                        || !Double.isFinite(travelTimeMinutes)
                        || travelTimeMinutes
                        > WALKING_MAX_MINUTES + EPSILON) {
                    continue;
                }
                reachable.add(candidates.get(toIndex));
            }
            neighbors.put(
                    candidates.get(fromIndex).getPlaceId(),
                    List.copyOf(reachable)
            );
        }
        return Map.copyOf(neighbors);
    }

    /** 모든 일정 유형에서 같은 DAY의 옵션 한 쌍당 허용할 최대 중복 수이다. */
    private int resolveDailyOverlapLimit(
            String travelCode,
            List<ValidatedDailyPlan> dailyPlans
    ) {
        return MAX_DAILY_OVERLAP_LIMIT;
    }

    /** 한 전략의 모든 날짜를 선발하고 다음 전략에서 사용할 장소 ID를 반환한다. */
    private SequentialSelection createSequentialSelection(
            Map<LocalDate, DailyRouteContext> routeContexts,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            Set<Long> hardExcludedPlaceIds
    ) {
        List<PlaceCandidateDto> selectedCandidates = new ArrayList<>();
        Map<LocalDate, Long> preferredFirstPlaceIds = new TreeMap<>();
        Set<Long> selectedAcrossDates = new LinkedHashSet<>();

        for (Map.Entry<LocalDate, DailyRouteContext> entry
                : routeContexts.entrySet()) {
            SelectionConstraints constraints = new SelectionConstraints(
                    Set.copyOf(selectedAcrossDates),
                    Set.copyOf(hardExcludedPlaceIds),
                    true
            );
            DailyPick dailyPick = selectDailyPlaces(
                    entry.getValue(),
                    strategy,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit,
                    constraints
            );
            Set<Long> selectedIds = dailyPick.placeCandidates().stream()
                    .map(PlaceCandidateDto::getPlaceId)
                    .collect(java.util.stream.Collectors.toSet());
            Set<Long> blockedAlternativeIds =
                    createBlockedAlternativeIds(
                            entry.getKey(),
                            previouslyRecommendedPlaceIds,
                            generatedOptionPlaces,
                            constraints
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
            selectedAcrossDates.addAll(selectedIds);
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
            Long resultId,
            Set<Long> hardExcludedPlaceIds
    ) {
        return createWalkingSequentialSelection(
                routeContexts,
                strategy,
                previouslyRecommendedPlaceIds,
                generatedOptionPlaces,
                dailyOverlapLimit,
                resultId,
                hardExcludedPlaceIds,
                false
        );
    }

    /**
     * 후보가 빠듯한 다일 일정에서는 앞 날짜가 항상 고득점 장소를 먼저 가져가면
     * 날짜별 장소 배치가 같은 형태로만 반복된다. 날짜 순서를 양방향으로 탐색해
     * 같은 후보 풀도 DAY 사이에서 회전 배치할 수 있게 한다.
     */
    private SequentialSelection createWalkingSequentialSelection(
            Map<LocalDate, DailyRouteContext> routeContexts,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            Long resultId,
            Set<Long> hardExcludedPlaceIds,
            boolean reverseDateOrder
    ) {
        List<PlaceCandidateDto> selectedCandidates = new ArrayList<>();
        Map<LocalDate, Long> preferredFirstPlaceIds = new TreeMap<>();
        Set<Long> selectedAcrossDates = new LinkedHashSet<>();

        List<Map.Entry<LocalDate, DailyRouteContext>> orderedContexts =
                new ArrayList<>(routeContexts.entrySet());
        if (reverseDateOrder) {
            java.util.Collections.reverse(orderedContexts);
        }
        for (Map.Entry<LocalDate, DailyRouteContext> entry
                : orderedContexts) {
            SelectionConstraints constraints = new SelectionConstraints(
                    Set.copyOf(selectedAcrossDates),
                    Set.copyOf(hardExcludedPlaceIds),
                    true
            );
            DailyPick dailyPick;
            try {
                dailyPick = selectWalkingDailyPlaces(
                        entry.getValue(),
                        strategy,
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit,
                        resultId,
                        constraints
                );
            } catch (IllegalStateException exception) {
                if (!hardExcludedPlaceIds.isEmpty()) {
                    return null;
                }
                throw exception;
            }
            Set<Long> selectedIds = dailyPick.placeCandidates().stream()
                    .map(PlaceCandidateDto::getPlaceId)
                    .collect(java.util.stream.Collectors.toSet());
            Set<Long> blockedAlternativeIds = createBlockedAlternativeIds(
                    entry.getKey(),
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    constraints
            );

            // beam search가 찾은 순서가 이미 도보 20분 이내 경로이므로 순서를 유지한다.
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
            selectedAcrossDates.addAll(selectedIds);
        }

        return new SequentialSelection(
                List.copyOf(selectedCandidates),
                Map.copyOf(preferredFirstPlaceIds)
        );
    }

    /**
     * 도보 코스는 모든 인접 구간이 20분 이내인 경로만 허용한다.
     *
     * <p>같은 장소 수에서는 먼저 총 도보시간이 구간 수×15분 이하인 경로를 찾고,
     * 없을 때만 구간 수×18분 이하로 완화한다. 두 기준으로도 만들 수 없으면
     * 장소 수를 줄여 다시 시도한다. 코스로 볼 수 있는 최소 3곳은 보장하며,
     * 평균 18분과 구간당 20분 상한은 장소 수가 줄어도 더 완화하지 않는다.</p>
     */
    private DailyPick selectWalkingDailyPlaces(
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            Long resultId,
            SelectionConstraints constraints
    ) {
        int requestedPlaceCount = context.plan().targetPlaceCount();
        int minimumPlaceCount = Math.min(
                requestedPlaceCount,
                MIN_PLACES_PER_DAY
        );
        SelectionConstraints noSameCourseReuse =
                constraints.withForbidSameCourseReuse(true);

        // 같은 장소 수에서는 옵션 간 중복 0개를 먼저 찾고, 불가능할 때만
        // 최대 1개까지 허용한다. 둘 다 실패한 뒤에만 장소 수를 줄인다.
        for (int placeCount = requestedPlaceCount;
             placeCount >= minimumPlaceCount;
             placeCount--) {
            List<Map<String, Integer>> targetVariants =
                    createCategoryTargetsForPlaceCount(
                            context,
                            placeCount
                    );

            for (int allowedOverlap = 0;
                 allowedOverlap <= dailyOverlapLimit;
                 allowedOverlap++) {
                List<Double> averageLimits = List.of(
                        WALKING_TARGET_AVERAGE_MINUTES,
                        WALKING_RELAXED_AVERAGE_MINUTES
                );

                for (double averageLimit : averageLimits) {
                    DailyPick pick = findWalkingPickWithinAverage(
                            context,
                            strategy,
                            previouslyRecommendedPlaceIds,
                            generatedOptionPlaces,
                            allowedOverlap,
                            placeCount,
                            targetVariants,
                            averageLimit,
                            noSameCourseReuse
                    );
                    if (pick == null) {
                        continue;
                    }
                    if (allowedOverlap > 0) {
                        log.warn(
                                "같은 DAY의 다른 추천 코스와 중복 0개 경로를 "
                                        + "만들 수 없어 최대 1개 중복을 허용했습니다: "
                                        + "resultId={}, strategy={}, visitDate={}, "
                                        + "selectedPlaces={}",
                                resultId,
                                strategy,
                                context.plan().visitDate(),
                                placeCount
                        );
                    }
                    return logAndReturnWalkingPick(
                            pick,
                            context,
                            strategy,
                            resultId,
                            requestedPlaceCount,
                            placeCount,
                            averageLimit
                    );
                }
            }
        }

        throw new IllegalStateException(
                "도보 20분 이내, DAY 간 무중복, 옵션 간 최대 1개 중복 조건으로 "
                        + "코스를 만들 수 없습니다. resultId=" + resultId
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
            double maximumAverageMinutes,
            SelectionConstraints constraints
    ) {
        double maximumTotalMinutes = walkingTotalTimeLimit(
                placeCount,
                maximumAverageMinutes
        );
        DailyPick bestFallbackPick = null;
        for (Map<String, Integer> categoryTargets : targetVariants) {
            DailyPick pick = findWalkingDailyPlaces(
                    context,
                    strategy,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit,
                    placeCount,
                    categoryTargets,
                    maximumTotalMinutes,
                    constraints
            );
            if (pick != null) {
                if (constraints.forbidSameCourseReuse()) {
                    return pick;
                }
                if (bestFallbackPick == null
                        || compareDailyPicks(
                        pick,
                        bestFallbackPick,
                        strategy,
                        context
                ) < 0) {
                    bestFallbackPick = pick;
                }
            }
        }
        return bestFallbackPick;
    }

    private DailyPick logAndReturnWalkingPick(
            DailyPick pick,
            DailyRouteContext context,
            OptionStrategy strategy,
            Long resultId,
            int requestedPlaceCount,
            int selectedPlaceCount,
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

        if (pick.sameCourseOverlap() > 0) {
            log.warn(
                    "DAY 간 무중복 도보 경로가 없어 최소 중복을 허용했습니다: "
                            + "resultId={}, strategy={}, visitDate={}, "
                            + "sameCourseOverlap={}",
                    resultId,
                    strategy,
                    context.plan().visitDate(),
                    pick.sameCourseOverlap()
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

    private double walkingTotalTimeLimit(
            int placeCount,
            double maximumAverageMinutes
    ) {
        return Math.max(0, placeCount - 1) * maximumAverageMinutes;
    }

    /** 지정한 장소 수·카테고리 배분·총시간 상한으로 도보 경로를 찾는다. */
    private DailyPick findWalkingDailyPlaces(
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            int targetPlaceCount,
            Map<String, Integer> categoryTargets,
            double maximumTotalMinutes,
            SelectionConstraints constraints
    ) {
        List<WalkingPathState> states = new ArrayList<>();
        for (PlaceCandidateDto first : context.plan().placeCandidates()) {
            if (constraints.isBlocked(first.getPlaceId())) {
                continue;
            }
            if (wouldExceedDailyOverlap(
                    first.getPlaceId(),
                    List.of(),
                    context.plan().visitDate(),
                    generatedOptionPlaces,
                    dailyOverlapLimit
            )) {
                continue;
            }
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
                    maximumTotalMinutes,
                    constraints
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
                dailyOverlapLimit,
                constraints
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
                                dailyOverlapLimit,
                                constraints
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
                            maximumTotalMinutes,
                            constraints
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
                    dailyOverlapLimit,
                    constraints
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
                            dailyOverlapLimit,
                            constraints
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
                maximumTotalMinutes,
                constraints
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
            double maximumTotalMinutes,
            SelectionConstraints constraints
    ) {
        List<WalkingPathState> initialStates = new ArrayList<>();
        for (PlaceCandidateDto first : context.plan().placeCandidates()) {
            if (constraints.isBlocked(first.getPlaceId())) {
                continue;
            }
            if (wouldExceedDailyOverlap(
                    first.getPlaceId(),
                    List.of(),
                    context.plan().visitDate(),
                    generatedOptionPlaces,
                    dailyOverlapLimit
            )) {
                continue;
            }
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
                    maximumTotalMinutes,
                    constraints
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
                dailyOverlapLimit,
                constraints
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
                    deadStates,
                    constraints
            );
            if (completed != null) {
                return toWalkingDailyPick(
                        completed,
                        context,
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit,
                        constraints
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
            Set<String> deadStates,
            SelectionConstraints constraints
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
                dailyOverlapLimit,
                constraints
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
                    maximumTotalMinutes,
                    constraints
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
                    deadStates,
                    constraints
            );
            if (completed != null) {
                return completed;
            }
        }
        return null;
    }

    /** 카테고리 종류를 유지한 단 하나의 장소 수 감소안만 반환한다. */
    private List<Map<String, Integer>> createCategoryTargetsForPlaceCount(
            DailyRouteContext context,
            int targetPlaceCount
    ) {
        return List.of(deriveFinalCategoryTargets(
                context.plan().categoryTargets(),
                targetPlaceCount
        ));
    }

    private List<WalkingPathState> retainBestWalkingStates(
            List<WalkingPathState> candidates,
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            SelectionConstraints constraints
    ) {
        Comparator<WalkingPathState> comparator = (left, right) -> {
            DailyPick leftPick = toWalkingDailyPick(
                    left,
                    context,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit,
                    constraints
            );
            DailyPick rightPick = toWalkingDailyPick(
                    right,
                    context,
                    previouslyRecommendedPlaceIds,
                    generatedOptionPlaces,
                    dailyOverlapLimit,
                    constraints
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
        };

        Map<String, WalkingPathState> unique = new LinkedHashMap<>();
        for (WalkingPathState state : candidates) {
            String stateKey = walkingStateKey(state);
            WalkingPathState previous = unique.get(stateKey);
            if (previous == null
                    || comparator.compare(state, previous) < 0) {
                unique.put(stateKey, state);
            }
        }

        PriorityQueue<WalkingPathState> bestStates =
                new PriorityQueue<>(
                        WALKING_PATH_BEAM_WIDTH,
                        comparator.reversed()
                );
        for (WalkingPathState state : unique.values()) {
            if (bestStates.size() < WALKING_PATH_BEAM_WIDTH) {
                bestStates.add(state);
                continue;
            }
            if (comparator.compare(state, bestStates.peek()) < 0) {
                bestStates.poll();
                bestStates.add(state);
            }
        }

        List<WalkingPathState> retained =
                new ArrayList<>(bestStates);
        retained.sort(comparator);
        return retained;
    }

    private List<PlaceCandidateDto> walkingExpansionCandidates(
            WalkingPathState state,
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            SelectionConstraints constraints
    ) {
        PlaceCandidateDto current = state.path().get(
                state.path().size() - 1
        );
        List<PlaceCandidateDto> available = context
                .walkingNeighborCandidates()
                .getOrDefault(
                        current.getPlaceId(),
                        List.of()
                )
                .stream()
                .filter(candidate -> !state.selectedIds().contains(
                        candidate.getPlaceId()
                ))
                .filter(candidate -> !constraints.isBlocked(
                        candidate.getPlaceId()
                ))
                .filter(candidate -> !wouldExceedDailyOverlap(
                        candidate.getPlaceId(),
                        state.path(),
                        context.plan().visitDate(),
                        generatedOptionPlaces,
                        dailyOverlapLimit
                ))
                .filter(candidate -> state.remainingTargets().getOrDefault(
                        candidate.getCategory(),
                        0
                ) > 0)
                .sorted((left, right) -> compareWalkingExpansionCandidates(
                        left,
                        right,
                        state,
                        context,
                        strategy,
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        dailyOverlapLimit,
                        constraints
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
            int dailyOverlapLimit,
            SelectionConstraints constraints
    ) {
        int comparison = Boolean.compare(
                constraints.isSameCourseReuse(left.getPlaceId()),
                constraints.isSameCourseReuse(right.getPlaceId())
        );
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
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
            double maximumTotalMinutes,
            SelectionConstraints constraints
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
                    .filter(candidate -> !constraints.isBlocked(
                            candidate.getPlaceId()
                    ))
                    .count();
            if (available < remaining) {
                return false;
            }
        }
        return hasReachableWalkingCategoryTargets(
                state,
                context,
                constraints
        );
    }

    /**
     * 현재 경로 끝에서 도보 20분 이내 간선으로 이어지는 지역 묶음 안에
     * 남은 카테고리가 모두 있는지 확인한다. 서울 전역의 후보 수만 세는 기존
     * 검사와 달리 실제로 연결되지 않는 구의 후보 때문에 깊은 탐색을 반복하지 않는다.
     */
    private boolean hasReachableWalkingCategoryTargets(
            WalkingPathState state,
            DailyRouteContext context,
            SelectionConstraints constraints
    ) {
        if (state.remainingTargets().values().stream()
                .allMatch(value -> value == 0)) {
            return true;
        }

        PlaceCandidateDto start = state.path().get(state.path().size() - 1);
        Deque<PlaceCandidateDto> pending = new ArrayDeque<>();
        Set<Long> reachableIds = new LinkedHashSet<>();
        pending.add(start);
        reachableIds.add(start.getPlaceId());

        while (!pending.isEmpty()) {
            PlaceCandidateDto current = pending.removeFirst();
            for (PlaceCandidateDto candidate
                    : context.walkingNeighborCandidates().getOrDefault(
                    current.getPlaceId(),
                    List.of()
            )) {
                if (reachableIds.contains(candidate.getPlaceId())
                        || state.selectedIds().contains(candidate.getPlaceId())
                        || constraints.isBlocked(candidate.getPlaceId())
                        || state.remainingTargets().getOrDefault(
                                candidate.getCategory(),
                                0
                        ) < 1) {
                    continue;
                }
                reachableIds.add(candidate.getPlaceId());
                pending.addLast(candidate);
            }
        }

        for (Map.Entry<String, Integer> target
                : state.remainingTargets().entrySet()) {
            if (target.getValue() < 1) {
                continue;
            }
            long reachable = context.plan().placeCandidates().stream()
                    .filter(candidate -> reachableIds.contains(
                            candidate.getPlaceId()
                    ))
                    .filter(candidate -> candidate.getCategory().equals(
                            target.getKey()
                    ))
                    .count();
            if (reachable < target.getValue()) {
                return false;
            }
        }
        return true;
    }

    private boolean walkingTargetsSatisfied(WalkingPathState state) {
        return state.remainingTargets().values().stream()
                .allMatch(value -> value == 0);
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
            int dailyOverlapLimit,
            SelectionConstraints constraints
    ) {
        PickReuseMetrics reuseMetrics = calculatePickReuseMetrics(
                state.path(),
                context.plan().visitDate(),
                previouslyRecommendedPlaceIds,
                generatedOptionPlaces,
                dailyOverlapLimit,
                constraints
        );
        return new DailyPick(
                List.copyOf(state.path()),
                state.path().get(0).getPlaceId(),
                state.recommendationScore(),
                state.travelTimeMinutes(),
                state.distanceKm(),
                reuseMetrics.sameCourseOverlap(),
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
     * 찾은 도보 순서를 다시 섞지 않고 경로값을 응답 DTO에 적용한다.
     * ORS 실패 시에는 같은 시간 상한을 통과한 추정 구간을 배지와 함께 유지한다.
     */
    private CourseOptimizeResponse optimizeFixedWalkingSelection(
            SequentialSelection selection,
            OptionStrategy strategy,
            Long resultId
    ) {
        CourseOptimizeResponse optimized = resolveFixedWalkingRouteDetails(
                selection
        );
        try {
            validateResolvedFixedWalkingSelection(
                    selection,
                    optimized,
                    strategy,
                    resultId,
                    "고정 경로 상세 계산"
            );
            return optimized;
        } catch (IllegalStateException initialFailure) {
            CourseOptimizeResponse repaired = repairActualWalkingSelection(
                    selection,
                    strategy,
                    resultId,
                    optimized
            );
            if (repaired != null) {
                log.warn(
                        "추정 경로와 실제 도보시간 차이로 탈락한 후보를 "
                                + "문제 DAY의 장소를 줄여 복구했습니다: "
                                + "resultId={}, strategy={}, beforePlaces={}, "
                                + "afterPlaces={}",
                        resultId,
                        strategy,
                        selection.placeCandidates().size(),
                        repaired.getOptimizedPlaces().size()
                );
                return repaired;
            }
            Set<LocalDate> violationDates =
                    walkingLimitViolationDates(optimized);
            if (violationDates.isEmpty()) {
                throw initialFailure;
            }
            throw new ActualWalkingLimitException(
                    initialFailure.getMessage(),
                    violationDates,
                    initialFailure
            );
        }
    }

    /** 실제 경로에서 구간 20분 또는 DAY 평균 18분을 넘긴 날짜만 반환한다. */
    private Set<LocalDate> walkingLimitViolationDates(
            CourseOptimizeResponse optimized
    ) {
        Map<LocalDate, List<OptimizedPlaceDto>> placesByDate =
                optimized.getOptimizedPlaces().stream()
                        .filter(place -> !"HOTEL".equalsIgnoreCase(
                                place.getCategory()
                        ))
                        .collect(java.util.stream.Collectors.groupingBy(
                                OptimizedPlaceDto::getVisitDate,
                                TreeMap::new,
                                java.util.stream.Collectors.toList()
                        ));
        Set<LocalDate> violationDates = new LinkedHashSet<>();
        for (Map.Entry<LocalDate, List<OptimizedPlaceDto>> entry
                : placesByDate.entrySet()) {
            List<OptimizedPlaceDto> dailyPlaces = entry.getValue().stream()
                    .sorted(Comparator.comparing(
                            OptimizedPlaceDto::getVisitOrder
                    ))
                    .toList();
            int legCount = Math.max(0, dailyPlaces.size() - 1);
            if (legCount == 0) {
                continue;
            }
            double totalMinutes = 0.0;
            double maximumMinutes = 0.0;
            for (int index = 1; index < dailyPlaces.size(); index++) {
                double minutes = valueOrZero(
                        dailyPlaces.get(index)
                                .getTravelTimeFromPreviousMinutes()
                );
                totalMinutes += minutes;
                maximumMinutes = Math.max(maximumMinutes, minutes);
            }
            if (maximumMinutes > WALKING_MAX_MINUTES + EPSILON
                    || totalMinutes
                    > legCount * WALKING_RELAXED_AVERAGE_MINUTES
                    + EPSILON) {
                violationDates.add(entry.getKey());
            }
        }
        return Set.copyOf(violationDates);
    }

    private CourseOptimizeResponse resolveFixedWalkingRouteDetails(
            SequentialSelection selection
    ) {
        return courseOptimizationService.resolveFixedRouteDetails(
                CourseOptimizeRequest.builder()
                        .transportMode(TransportMode.WALKING)
                        .placeCandidates(selection.placeCandidates())
                        .build()
        );
    }

    private void validateResolvedFixedWalkingSelection(
            SequentialSelection selection,
            CourseOptimizeResponse optimized,
            OptionStrategy strategy,
            Long resultId,
            String stage
    ) {
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
        if (quality.violationCount() > 0) {
            throw new IllegalStateException(
                    "도보 하드 제한을 통과하지 못한 경로는 반환할 수 없습니다. "
                            + "resultId=" + resultId
                            + ", strategy=" + strategy
                            + ", overLimitLegs="
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
                stage
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
    }

    /**
     * 추정값은 통과했지만 실제 ORS 경로에서 일부 구간이 20분을 넘으면,
     * 위반 DAY에서 이동 부담이 큰 장소를 하나씩 제거해 실제 경로를 다시 검증한다.
     * 하루 3곳으로도 실제 상한을 지킬 수 없을 때만 최후 수단으로 2곳까지 줄이며,
     * 장소 구성 복제 금지와 옵션 간 전체 DAY 중복 검사는 최종 응답 조립 단계에서
     * 복구된 실제 장소 기준으로 다시 검사한다.
     */
    private CourseOptimizeResponse repairActualWalkingSelection(
            SequentialSelection originalSelection,
            OptionStrategy strategy,
            Long resultId,
            CourseOptimizeResponse failedOptimized
    ) {
        Deque<WalkingRepairState> pending = new ArrayDeque<>();
        enqueueWalkingRepairStates(
                pending,
                originalSelection,
                failedOptimized,
                1
        );
        Set<String> attempted = new LinkedHashSet<>();
        attempted.add(walkingSelectionSignature(originalSelection));
        int attempts = 0;

        while (!pending.isEmpty()
                && attempts < MAX_ACTUAL_WALKING_REPAIR_ATTEMPTS) {
            WalkingRepairState state = pending.removeFirst();
            String signature = walkingSelectionSignature(state.selection());
            if (!attempted.add(signature)) {
                continue;
            }
            attempts++;

            CourseOptimizeResponse optimized;
            try {
                optimized = resolveFixedWalkingRouteDetails(state.selection());
            } catch (IllegalArgumentException
                     | IllegalStateException exception) {
                continue;
            }
            try {
                validateResolvedFixedWalkingSelection(
                        state.selection(),
                        optimized,
                        strategy,
                        resultId,
                        "실제 도보 초과 후보 복구"
                );
                log.info(
                        "실제 도보 초과 후보 복구 완료: resultId={}, "
                                + "strategy={}, attempts={}, removedPlaces={}",
                        resultId,
                        strategy,
                        attempts,
                        originalSelection.placeCandidates().size()
                                - state.selection().placeCandidates().size()
                );
                return optimized;
            } catch (IllegalStateException exception) {
                if (state.depth() < MAX_ACTUAL_WALKING_REPAIR_DEPTH) {
                    enqueueWalkingRepairStates(
                            pending,
                            state.selection(),
                            optimized,
                            state.depth() + 1
                    );
                }
            }
        }
        return null;
    }

    private void enqueueWalkingRepairStates(
            Deque<WalkingRepairState> pending,
            SequentialSelection selection,
            CourseOptimizeResponse optimized,
            int depth
    ) {
        for (Long placeId : walkingRepairRemovalCandidates(optimized)) {
            SequentialSelection reduced = removePlaceFromWalkingSelection(
                    selection,
                    placeId
            );
            if (reduced != null) {
                pending.addLast(new WalkingRepairState(reduced, depth));
            }
        }
    }

    /** 위반 DAY에서 양옆 이동시간 합이 큰 장소부터 제거 후보로 만든다. */
    private List<Long> walkingRepairRemovalCandidates(
            CourseOptimizeResponse optimized
    ) {
        Map<LocalDate, List<OptimizedPlaceDto>> placesByDate =
                optimized.getOptimizedPlaces().stream()
                        .filter(place -> !"HOTEL".equalsIgnoreCase(
                                place.getCategory()
                        ))
                        .collect(java.util.stream.Collectors.groupingBy(
                                OptimizedPlaceDto::getVisitDate,
                                TreeMap::new,
                                java.util.stream.Collectors.toList()
                        ));
        List<WalkingRemovalCandidate> preferred = new ArrayList<>();
        List<WalkingRemovalCandidate> fallback = new ArrayList<>();

        for (List<OptimizedPlaceDto> unsorted : placesByDate.values()) {
            List<OptimizedPlaceDto> daily = unsorted.stream()
                    .sorted(Comparator.comparing(
                            OptimizedPlaceDto::getVisitOrder
                    ))
                    .toList();
            if (daily.size()
                    <= MIN_ACTUAL_WALKING_REPAIR_PLACES_PER_DAY) {
                continue;
            }
            double totalMinutes = 0.0;
            double maximumMinutes = 0.0;
            for (int index = 1; index < daily.size(); index++) {
                double minutes = valueOrZero(
                        daily.get(index)
                                .getTravelTimeFromPreviousMinutes()
                );
                totalMinutes += minutes;
                maximumMinutes = Math.max(maximumMinutes, minutes);
            }
            int legCount = daily.size() - 1;
            if (maximumMinutes <= WALKING_MAX_MINUTES + EPSILON
                    && totalMinutes
                    <= legCount * WALKING_RELAXED_AVERAGE_MINUTES
                    + EPSILON) {
                continue;
            }

            Map<String, Integer> categoryCounts = new LinkedHashMap<>();
            for (OptimizedPlaceDto place : daily) {
                categoryCounts.merge(
                        normalizeCategory(place.getCategory()),
                        1,
                        Integer::sum
                );
            }
            for (int index = 0; index < daily.size(); index++) {
                OptimizedPlaceDto place = daily.get(index);
                double previous = index == 0
                        ? 0.0
                        : valueOrZero(place
                        .getTravelTimeFromPreviousMinutes());
                double next = index + 1 >= daily.size()
                        ? 0.0
                        : valueOrZero(daily.get(index + 1)
                        .getTravelTimeFromPreviousMinutes());
                String category = normalizeCategory(place.getCategory());
                boolean protectedSingleCategory =
                        ("RESTAURANT".equals(category)
                                || "CAFE".equals(category))
                                && categoryCounts.getOrDefault(category, 0) <= 1;
                WalkingRemovalCandidate removal =
                        new WalkingRemovalCandidate(
                                place.getPlaceId(),
                                previous + next,
                                protectedSingleCategory
                        );
                if (protectedSingleCategory) {
                    fallback.add(removal);
                } else {
                    preferred.add(removal);
                }
            }
        }

        Comparator<WalkingRemovalCandidate> comparator = Comparator
                .comparingDouble(WalkingRemovalCandidate::travelBurden)
                .reversed()
                .thenComparing(WalkingRemovalCandidate::placeId);
        preferred.sort(comparator);
        fallback.sort(comparator);
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        preferred.forEach(candidate -> ordered.add(candidate.placeId()));
        fallback.forEach(candidate -> ordered.add(candidate.placeId()));
        return List.copyOf(ordered);
    }

    private SequentialSelection removePlaceFromWalkingSelection(
            SequentialSelection selection,
            Long placeId
    ) {
        List<PlaceCandidateDto> reduced = selection.placeCandidates().stream()
                .filter(candidate -> !candidate.getPlaceId().equals(placeId))
                .toList();
        if (reduced.size() == selection.placeCandidates().size()) {
            return null;
        }
        Map<LocalDate, Long> firstPlaceIds = new TreeMap<>();
        Map<LocalDate, Integer> countsByDate = new TreeMap<>();
        for (PlaceCandidateDto candidate : reduced) {
            countsByDate.merge(candidate.getVisitDate(), 1, Integer::sum);
            firstPlaceIds.putIfAbsent(
                    candidate.getVisitDate(),
                    candidate.getPlaceId()
            );
        }
        if (countsByDate.values().stream()
                .anyMatch(count -> count
                        < MIN_ACTUAL_WALKING_REPAIR_PLACES_PER_DAY)) {
            return null;
        }
        return new SequentialSelection(
                List.copyOf(reduced),
                Map.copyOf(firstPlaceIds)
        );
    }

    private String walkingSelectionSignature(
            SequentialSelection selection
    ) {
        return selection.placeCandidates().stream()
                .map(candidate -> candidate.getVisitDate()
                        + ":" + candidate.getPlaceId())
                .reduce((left, right) -> left + ">" + right)
                .orElse("");
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
            int dailyOverlapLimit,
            SelectionConstraints constraints
    ) {
        int requestedPlaceCount = context.plan().targetPlaceCount();
        int minimumPlaceCount = Math.min(
                requestedPlaceCount,
                MIN_PLACES_PER_DAY
        );

        for (int placeCount = requestedPlaceCount;
             placeCount >= minimumPlaceCount;
             placeCount--) {
            List<Map<String, Integer>> targetVariants =
                    createCategoryTargetsForPlaceCount(context, placeCount);

            for (int allowedOverlap = 0;
                 allowedOverlap <= dailyOverlapLimit;
                 allowedOverlap++) {
                DailyPick best = null;
                for (Map<String, Integer> categoryTargets : targetVariants) {
                    DailyPick candidatePick = selectDailyPlacesForTargets(
                            context,
                            strategy,
                            previouslyRecommendedPlaceIds,
                            generatedOptionPlaces,
                            allowedOverlap,
                            constraints,
                            placeCount,
                            categoryTargets
                    );
                    if (candidatePick != null
                            && (best == null || compareDailyPicks(
                            candidatePick,
                            best,
                            strategy,
                            context
                    ) < 0)) {
                        best = candidatePick;
                    }
                }
                if (best == null) {
                    continue;
                }
                if (allowedOverlap > 0) {
                    log.warn(
                            "같은 DAY의 다른 추천 코스와 중복 0개 조합을 만들 수 없어 "
                                    + "최대 1개 중복을 허용했습니다: strategy={}, "
                                    + "visitDate={}, selectedPlaces={}",
                            strategy,
                            context.plan().visitDate(),
                            placeCount
                    );
                }
                if (placeCount < requestedPlaceCount) {
                    log.warn(
                            "중복 제한을 지키기 위해 하루 장소 수를 줄였습니다: "
                                    + "strategy={}, visitDate={}, requestedPlaces={}, "
                                    + "selectedPlaces={}",
                            strategy,
                            context.plan().visitDate(),
                            requestedPlaceCount,
                            placeCount
                    );
                }
                return best;
            }
        }

        throw new IllegalArgumentException(
                "DAY 간 무중복, 옵션 간 최대 1개 중복 조건으로 코스를 만들 수 없습니다. "
                        + "visitDate=" + context.plan().visitDate()
        );
    }

    private DailyPick selectDailyPlacesForTargets(
            DailyRouteContext context,
            OptionStrategy strategy,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int allowedOverlap,
            SelectionConstraints constraints,
            int targetPlaceCount,
            Map<String, Integer> categoryTargets
    ) {
        List<PlaceCandidateDto> possibleFirstPlaces =
                context.plan().placeCandidates().stream()
                        .filter(candidate -> categoryTargets
                                .getOrDefault(candidate.getCategory(), 0) > 0)
                        .filter(candidate -> !constraints.isBlocked(
                                candidate.getPlaceId()
                        ))
                        .filter(candidate -> !wouldExceedDailyOverlap(
                                candidate.getPlaceId(),
                                List.of(),
                                context.plan().visitDate(),
                                generatedOptionPlaces,
                                allowedOverlap
                        ))
                        .toList();

        DailyPick best = null;
        for (PlaceCandidateDto firstCandidate : possibleFirstPlaces) {
            DailyPick candidatePick;
            try {
                candidatePick = buildGreedyDailyPick(
                        context,
                        strategy,
                        firstCandidate,
                        previouslyRecommendedPlaceIds,
                        generatedOptionPlaces,
                        allowedOverlap,
                        constraints,
                        targetPlaceCount,
                        categoryTargets
                );
            } catch (IllegalArgumentException ignored) {
                continue;
            }
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
        return best;
    }

    /** 한 출발 후보에서 남은 카테고리 수량을 가장 좋은 다음 장소로 하나씩 채운다. */
    private DailyPick buildGreedyDailyPick(
            DailyRouteContext context,
            OptionStrategy strategy,
            PlaceCandidateDto firstCandidate,
            Set<Long> previouslyRecommendedPlaceIds,
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            int dailyOverlapLimit,
            SelectionConstraints constraints,
            int targetPlaceCount,
            Map<String, Integer> categoryTargets
    ) {
        Map<String, Integer> remainingTargets =
                new LinkedHashMap<>(categoryTargets);
        List<PlaceCandidateDto> selected = new ArrayList<>();
        Set<Long> selectedIds = new LinkedHashSet<>();

        addSelectedCandidate(
                firstCandidate,
                selected,
                selectedIds,
                remainingTargets
        );

        while (selected.size() < targetPlaceCount) {
            List<PlaceCandidateDto> available =
                    context.plan().placeCandidates().stream()
                            .filter(candidate -> !selectedIds.contains(
                                    candidate.getPlaceId()
                            ))
                            .filter(candidate -> !constraints.isBlocked(
                                    candidate.getPlaceId()
                            ))
                            .filter(candidate -> !wouldExceedDailyOverlap(
                                    candidate.getPlaceId(),
                                    selected,
                                    context.plan().visitDate(),
                                    generatedOptionPlaces,
                                    dailyOverlapLimit
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
                            dailyOverlapLimit,
                            constraints
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
                dailyOverlapLimit,
                constraints
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
                reuseMetrics.sameCourseOverlap(),
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
            int dailyOverlapLimit,
            SelectionConstraints constraints
    ) {
        int comparison = Boolean.compare(
                constraints.isSameCourseReuse(left.getPlaceId()),
                constraints.isSameCourseReuse(right.getPlaceId())
        );
        if (comparison != 0) {
            return comparison;
        }
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
        comparison = Integer.compare(leftRestriction, rightRestriction);
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
            for (Set<Long> usedOnAnyDate : optionPlaces.values()) {
                long overlap = selectedIds.stream()
                        .filter(usedOnAnyDate::contains)
                        .count();
                if (usedOnAnyDate.contains(candidatePlaceId)) {
                    overlap++;
                }
                if (overlap > dailyOverlapLimit) {
                    return true;
                }
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
                .flatMap(option -> option.values().stream())
                .filter(dayPlaces -> dayPlaces.contains(placeId))
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
                left.sameCourseOverlap(),
                right.sameCourseOverlap()
        );
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
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
            int dailyOverlapLimit,
            SelectionConstraints constraints
    ) {
        Set<Long> selectedIds = selected.stream()
                .map(PlaceCandidateDto::getPlaceId)
                .collect(java.util.stream.Collectors.toSet());
        int sameCourseOverlap = (int) selectedIds.stream()
                .filter(constraints.sameCoursePlaceIds()::contains)
                .count();
        int previousCount = (int) selectedIds.stream()
                .filter(previouslyRecommendedPlaceIds::contains)
                .count();
        int overlapExcess = 0;
        int totalOverlap = 0;

        for (Map<LocalDate, Set<Long>> optionPlaces
                : generatedOptionPlaces) {
            for (Set<Long> usedOnAnyDate : optionPlaces.values()) {
                int overlap = (int) selectedIds.stream()
                        .filter(usedOnAnyDate::contains)
                        .count();
                totalOverlap += overlap;
                overlapExcess = Math.max(
                        overlapExcess,
                        Math.max(0, overlap - dailyOverlapLimit)
                );
            }
        }
        return new PickReuseMetrics(
                sameCourseOverlap,
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
            List<Map<LocalDate, Set<Long>>> generatedOptionPlaces,
            SelectionConstraints constraints
    ) {
        Set<Long> blocked = new LinkedHashSet<>(
                previouslyRecommendedPlaceIds
        );
        blocked.addAll(constraints.sameCoursePlaceIds());
        blocked.addAll(constraints.hardExcludedPlaceIds());
        for (Map<LocalDate, Set<Long>> optionPlaces
                : generatedOptionPlaces) {
            optionPlaces.values().forEach(blocked::addAll);
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
     * 최종 도보 DAY마다 일반 장소는 구간 20분·평균 18분 상한을 검증한다.
     * 숙소 구간은 일반 일정과 분리해 최대 30분까지만 허용한다.
     * ORS 실패로 예상값을 사용한 구간도 같은 시간 상한을 적용하고 예상 배지를 유지한다.
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
            int ordinaryLegCount = 0;
            int ordinaryEstimatedLegCount = 0;
            double ordinaryTotalMinutes = 0.0;
            double ordinaryMaximumMinutes = 0.0;
            int hotelLegCount = 0;
            int hotelEstimatedLegCount = 0;
            double hotelMaximumMinutes = 0.0;

            for (OptimizedPlaceDto place : dailyPlaces) {
                if (valueOrZero(place.getVisitOrder()) <= 1) {
                    continue;
                }
                double minutes = valueOrZero(
                        place.getTravelTimeFromPreviousMinutes()
                );
                boolean estimated = Boolean.TRUE.equals(
                        place.getRouteEstimated()
                );
                if ("HOTEL".equalsIgnoreCase(place.getCategory())) {
                    hotelLegCount++;
                    hotelMaximumMinutes = Math.max(
                            hotelMaximumMinutes,
                            minutes
                    );
                    if (estimated) {
                        hotelEstimatedLegCount++;
                    }
                    continue;
                }

                ordinaryLegCount++;
                ordinaryTotalMinutes += minutes;
                ordinaryMaximumMinutes = Math.max(
                        ordinaryMaximumMinutes,
                        minutes
                );
                if (estimated) {
                    ordinaryEstimatedLegCount++;
                }
            }

            double maximumOrdinaryTotalMinutes = ordinaryLegCount
                    * WALKING_RELAXED_AVERAGE_MINUTES;
            boolean ordinaryViolation =
                    ordinaryMaximumMinutes > WALKING_MAX_MINUTES + EPSILON
                    || ordinaryTotalMinutes
                    > maximumOrdinaryTotalMinutes + EPSILON;
            boolean hotelViolation = hotelMaximumMinutes
                    > HOTEL_WALKING_MAX_MINUTES + EPSILON;
            if (ordinaryViolation || hotelViolation) {
                throw new IllegalStateException(
                        "도보 제한을 벗어난 DAY는 반환할 수 없습니다. "
                                + "resultId=" + resultId
                                + ", strategy=" + strategy
                                + ", stage=" + stage
                                + ", visitDate=" + entry.getKey()
                                + ", places=" + dailyPlaces.size()
                                + ", ordinaryLegs=" + ordinaryLegCount
                                + ", ordinaryTotalMinutes="
                                + round(ordinaryTotalMinutes, 1)
                                + ", ordinaryAverageMinutes="
                                + round(
                                ordinaryLegCount == 0
                                        ? 0.0
                                        : ordinaryTotalMinutes
                                        / ordinaryLegCount,
                                1
                        )
                                + ", ordinaryMaximumMinutes="
                                + round(ordinaryMaximumMinutes, 1)
                                + ", ordinaryEstimatedLegs="
                                + ordinaryEstimatedLegCount
                                + ", hotelLegs=" + hotelLegCount
                                + ", hotelMaximumMinutes="
                                + round(hotelMaximumMinutes, 1)
                                + ", hotelEstimatedLegs="
                                + hotelEstimatedLegCount
                );
            }

            log.info(
                    "도보 DAY 제한 검증 완료: resultId={}, strategy={}, "
                            + "stage={}, visitDate={}, places={}, "
                            + "ordinaryTotalMinutes={}, ordinaryAverageMinutes={}, "
                            + "ordinaryMaximumMinutes={}, hotelMaximumMinutes={}",
                    resultId,
                    strategy,
                    stage,
                    entry.getKey(),
                    dailyPlaces.size(),
                    round(ordinaryTotalMinutes, 1),
                    round(
                            ordinaryLegCount == 0
                                    ? 0.0
                                    : ordinaryTotalMinutes
                                    / ordinaryLegCount,
                            1
                    ),
                    round(ordinaryMaximumMinutes, 1),
                    round(hotelMaximumMinutes, 1)
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
            if (minutes > WALKING_MAX_MINUTES) {
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

    /**
     * 코스별 숙박일 마지막 장소와 다음 DAY 첫 장소를 모두 고려해 숙소를 선택한다.
     *
     * <p>도보는 일반 장소→숙소 도착 구간을 20분 이내로 우선하고 후보가 없을 때
     * 최대 30분까지 완화한다. 숙소→다음 DAY 첫 장소 출발 구간은 완화하지 않고
     * 모든 DAY에서 20분 이내인 숙소만 허용한다.</p>
     */
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
        List<OptimizedPlaceDto> firstPlaces =
                firstOrdinaryPlacesAfterFirstDay(optimized);
        List<HotelEvaluation> evaluations = createHotelEvaluations(
                hotelCandidates,
                lastPlaces,
                firstPlaces,
                optimized.getTransportMode()
        );
        if (optimized.getTransportMode() == TransportMode.WALKING) {
            // 숙소는 일반 방문 장소의 평균 15/18분 제한과 분리한다.
            // 모든 숙박일의 마지막 장소에서 20분 이내인 숙소를 우선하고,
            // 없을 때만 최대 30분까지 완화한다. 단, DAY2 이후 숙소에서
            // 첫 장소까지는 모든 날 20분 이내여야 하며 이 제한은 완화하지 않는다.
            List<HotelEvaluation> preferredHotels = evaluations.stream()
                    .filter(evaluation ->
                            evaluation.maximumArrivalTravelMinutes()
                                    <= HOTEL_WALKING_PREFERRED_MAX_MINUTES
                                    + EPSILON)
                    .filter(evaluation ->
                            evaluation.maximumDepartureTravelMinutes()
                                    <= HOTEL_DEPARTURE_WALKING_MAX_MINUTES
                                    + EPSILON)
                    .toList();
            List<HotelEvaluation> relaxedHotels = evaluations.stream()
                    .filter(evaluation ->
                            evaluation.maximumArrivalTravelMinutes()
                                    <= HOTEL_WALKING_MAX_MINUTES
                                    + EPSILON)
                    .filter(evaluation ->
                            evaluation.maximumDepartureTravelMinutes()
                                    <= HOTEL_DEPARTURE_WALKING_MAX_MINUTES
                                    + EPSILON)
                    .toList();

            if (!preferredHotels.isEmpty()) {
                evaluations = preferredHotels;
            } else if (!relaxedHotels.isEmpty()) {
                evaluations = relaxedHotels;
                log.info(
                        "도보 숙소 도착 20분 이내 후보가 없어 최대 30분 범위로 "
                                + "완화합니다. 숙소 출발 20분 상한은 유지합니다."
                );
            } else {
                double closestArrivalMinutes = evaluations.stream()
                        .mapToDouble(
                                HotelEvaluation::maximumArrivalTravelMinutes
                        )
                        .min()
                        .orElse(Double.NaN);
                double closestDepartureMinutes = evaluations.stream()
                        .mapToDouble(
                                HotelEvaluation::maximumDepartureTravelMinutes
                        )
                        .min()
                        .orElse(Double.NaN);
                log.warn(
                        "숙소 도착 30분·다음 DAY 출발 20분 조건을 모두 "
                                + "만족하는 숙소가 없어 코스에 적용하지 않습니다: "
                                + "closestArrivalMaximumMinutes={}, "
                                + "closestDepartureMaximumMinutes={}",
                        Double.isFinite(closestArrivalMinutes)
                                ? round(closestArrivalMinutes, 1)
                                : "unavailable",
                        Double.isFinite(closestDepartureMinutes)
                                ? round(closestDepartureMinutes, 1)
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
            List<OptimizedPlaceDto> firstPlaces,
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
                || (lastPlaces.isEmpty() && firstPlaces.isEmpty())) {
            return hotelCandidates.stream()
                    .map(hotel -> new HotelEvaluation(
                            hotel,
                            averageDistances.get(hotel.getPlaceId()),
                            Double.POSITIVE_INFINITY,
                            Double.POSITIVE_INFINITY,
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
        int firstDeparturePlaceIndex = matrixCandidates.size();
        for (OptimizedPlaceDto firstPlace : firstPlaces) {
            matrixCandidates.add(toRouteCandidate(firstPlace));
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
            double maximumArrivalMinutes = 0.0;
            double totalArrivalMinutes = 0.0;
            double maximumDepartureMinutes = 0.0;
            double totalDepartureMinutes = 0.0;
            boolean estimated = false;
            for (int lastIndex = 0;
                 lastIndex < lastPlaces.size();
                 lastIndex++) {
                double minutes = matrix.getTravelTimeMinutes(
                        lastIndex,
                        hotelIndex
                );
                maximumArrivalMinutes = Math.max(
                        maximumArrivalMinutes,
                        minutes
                );
                totalArrivalMinutes += minutes;
                estimated |= matrix.isEstimated(lastIndex, hotelIndex);
            }
            for (int firstOffset = 0;
                 firstOffset < firstPlaces.size();
                 firstOffset++) {
                int firstPlaceIndex =
                        firstDeparturePlaceIndex + firstOffset;
                double minutes = matrix.getTravelTimeMinutes(
                        hotelIndex,
                        firstPlaceIndex
                );
                maximumDepartureMinutes = Math.max(
                        maximumDepartureMinutes,
                        minutes
                );
                totalDepartureMinutes += minutes;
                estimated |= matrix.isEstimated(
                        hotelIndex,
                        firstPlaceIndex
                );
            }
            PlaceCandidateDto hotel = hotelCandidates.get(hotelOffset);
            evaluations.add(new HotelEvaluation(
                    hotel,
                    averageDistances.get(hotel.getPlaceId()),
                    maximumArrivalMinutes,
                    lastPlaces.isEmpty()
                            ? 0.0
                            : totalArrivalMinutes / lastPlaces.size(),
                    maximumDepartureMinutes,
                    firstPlaces.isEmpty()
                            ? 0.0
                            : totalDepartureMinutes / firstPlaces.size(),
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
                .region(place.getRegion())
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

    /**
     * DAY2 이후의 숙소 출발 도보 구간을 검사할 수 있도록 각 DAY 첫 일반 장소를
     * 날짜순으로 반환한다.
     */
    private List<OptimizedPlaceDto> firstOrdinaryPlacesAfterFirstDay(
            CourseOptimizeResponse optimized
    ) {
        if (optimized == null
                || optimized.getOptimizedPlaces() == null
                || optimized.getOptimizedPlaces().isEmpty()) {
            return List.of();
        }
        LocalDate firstDate = optimized.getOptimizedPlaces().stream()
                .map(OptimizedPlaceDto::getVisitDate)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        Map<LocalDate, OptimizedPlaceDto> firstByDate = new TreeMap<>();
        for (OptimizedPlaceDto place : optimized.getOptimizedPlaces()) {
            if (place.getVisitDate().equals(firstDate)
                    || "HOTEL".equalsIgnoreCase(place.getCategory())) {
                continue;
            }
            OptimizedPlaceDto current = firstByDate.get(
                    place.getVisitDate()
            );
            if (current == null
                    || valueOrZero(place.getVisitOrder())
                    < valueOrZero(current.getVisitOrder())) {
                firstByDate.put(place.getVisitDate(), place);
            }
        }
        return List.copyOf(firstByDate.values());
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

    /**
     * 최종 표시 장소 기준으로 중복 제한을 다시 검증한다.
     *
     * <p>같은 옵션의 날짜 간 중복은 0개가 하드 조건이다. 이전 옵션은 날짜가
     * 같거나 달라도 DAY 조합 한 쌍당 {@code dailyOverlapLimit}개까지만 허용한다. 제한을 넘긴
     * 장소는 현재 옵션의 재생성에서 완전히 제외할 ID로 반환한다.</p>
     */
    private FinalOverlapViolation findFinalOverlapViolation(
            Map<LocalDate, Set<Long>> current,
            List<Map<LocalDate, Set<Long>>> previousOptions,
            int dailyOverlapLimit
    ) {
        Set<Long> placeIdsToExclude = new LinkedHashSet<>();
        List<String> reasons = new ArrayList<>();

        // 같은 코스의 다른 DAY는 장소 중복 0개를 끝까지 유지한다.
        List<Map.Entry<LocalDate, Set<Long>>> currentDays =
                new ArrayList<>(current.entrySet());
        for (int leftIndex = 0;
             leftIndex < currentDays.size();
             leftIndex++) {
            for (int rightIndex = leftIndex + 1;
                 rightIndex < currentDays.size();
                 rightIndex++) {
                Map.Entry<LocalDate, Set<Long>> left =
                        currentDays.get(leftIndex);
                Map.Entry<LocalDate, Set<Long>> right =
                        currentDays.get(rightIndex);
                Set<Long> overlap = intersectPlaceIds(
                        left.getValue(),
                        right.getValue()
                );
                if (overlap.isEmpty()) {
                    continue;
                }
                chooseOneRetryExcludedPlace(overlap)
                        .ifPresent(placeIdsToExclude::add);
                reasons.add(
                        "sameCourseDifferentDays="
                                + left.getKey() + "/" + right.getKey()
                                + ":" + overlap
                );
            }
        }

        for (int optionIndex = 0;
             optionIndex < previousOptions.size();
             optionIndex++) {
            Map<LocalDate, Set<Long>> previous =
                    previousOptions.get(optionIndex);

            // 다른 코스는 날짜가 같거나 달라도 모든 DAY 조합을 비교한다.
            for (Map.Entry<LocalDate, Set<Long>> currentEntry
                    : current.entrySet()) {
                for (Map.Entry<LocalDate, Set<Long>> previousEntry
                        : previous.entrySet()) {
                    Set<Long> currentIds = currentEntry.getValue();
                    Set<Long> previousIds = previousEntry.getValue();
                    Set<Long> overlap = intersectPlaceIds(
                            currentIds,
                            previousIds
                    );

                    // 순서가 달라도 숙소 제외 장소 집합이 같으면 같은 하루 코스이다.
                    if (sameOrdinaryDayComposition(
                            currentIds,
                            previousIds
                    )) {
                        chooseOneRetryExcludedPlace(overlap)
                                .ifPresent(placeIdsToExclude::add);
                        reasons.add(
                                "identicalDayCompositionPreviousOption="
                                        + (optionIndex + 1)
                                        + ",currentDate="
                                        + currentEntry.getKey()
                                        + ",previousDate="
                                        + previousEntry.getKey()
                                        + ",places=" + overlap
                        );
                        continue;
                    }

                    if (overlap.size() > dailyOverlapLimit) {
                        chooseOneRetryExcludedPlace(overlap)
                                .ifPresent(placeIdsToExclude::add);
                        reasons.add(
                                "crossDayPreviousOption="
                                        + (optionIndex + 1)
                                        + ",currentDate="
                                        + currentEntry.getKey()
                                        + ",previousDate="
                                        + previousEntry.getKey()
                                        + ",overlap=" + overlap
                                        + ",limit=" + dailyOverlapLimit
                        );
                    }
                }
            }
        }

        if (placeIdsToExclude.isEmpty()) {
            return null;
        }
        return new FinalOverlapViolation(
                Set.copyOf(placeIdsToExclude),
                String.join("; ", reasons)
        );
    }

    /** 한 번의 재시도에서 장소를 과도하게 막지 않도록 위반 조합당 한 곳만 제외한다. */
    private java.util.Optional<Long> chooseOneRetryExcludedPlace(
            Set<Long> placeIds
    ) {
        return placeIds.stream().max(Long::compareTo);
    }

    private Set<Long> intersectPlaceIds(
            Set<Long> left,
            Set<Long> right
    ) {
        Set<Long> overlap = new LinkedHashSet<>();
        for (Long placeId : left) {
            if (right.contains(placeId)) {
                overlap.add(placeId);
            }
        }
        return overlap;
    }

    /** 숙소를 제외한 두 DAY의 장소 집합이 순서와 무관하게 완전히 같은지 확인한다. */
    private boolean sameOrdinaryDayComposition(
            Set<Long> left,
            Set<Long> right
    ) {
        return !left.isEmpty()
                && left.size() == right.size()
                && left.equals(right);
    }

    private void logOverlapResult(
            Long resultId,
            OptionStrategy strategy,
            Map<LocalDate, Set<Long>> current,
            List<Map<LocalDate, Set<Long>>> previousOptions,
            int dailyOverlapLimit
    ) {
        int maximumOverlap = 0;
        boolean identicalDayComposition = false;
        for (Map<LocalDate, Set<Long>> previous : previousOptions) {
            for (Set<Long> currentDay : current.values()) {
                for (Set<Long> previousDay : previous.values()) {
                    int overlap = intersectPlaceIds(
                            currentDay,
                            previousDay
                    ).size();
                    maximumOverlap = Math.max(maximumOverlap, overlap);
                    identicalDayComposition |=
                            sameOrdinaryDayComposition(
                                    currentDay,
                                    previousDay
                            );
                }
            }
        }

        if (maximumOverlap > dailyOverlapLimit) {
            log.warn(
                    "카테고리 후보 부족으로 코스 전체 DAY 조합의 중복 상한을 "
                            + "완화했습니다: resultId={}, strategy={}, "
                            + "maximumDayPairOverlap={}, limit={}, "
                            + "identicalDayComposition={}",
                    resultId,
                    strategy,
                    maximumOverlap,
                    dailyOverlapLimit,
                    identicalDayComposition
            );
        } else {
            log.info(
                    "코스 전체 DAY 조합 중복 검사 완료: resultId={}, strategy={}, "
                            + "maximumDayPairOverlap={}, limit={}, "
                            + "identicalDayComposition={}",
                    resultId,
                    strategy,
                    maximumOverlap,
                    dailyOverlapLimit,
                    identicalDayComposition
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

    private Long nextRetryExcludedPlaceId(
            String recommendationKey,
            Set<Long> alreadyExcludedPlaceIds,
            Map<LocalDate, DailyRouteContext> routeContexts
    ) {
        return extractPlaceIds(recommendationKey).stream()
                .filter(placeId -> !alreadyExcludedPlaceIds.contains(placeId))
                .filter(placeId -> routeContexts.values().stream()
                        .anyMatch(context -> context.candidateIndexes()
                                .containsKey(placeId)))
                .sorted()
                .findFirst()
                .orElse(null);
    }

    /** 숙소를 제외한 실제 표시 장소로 코스 동일성 비교용 키를 만든다. */
    private String createOrdinaryOptimizedCompositionSignature(
            List<OptimizedPlaceDto> places,
            TransportMode transportMode
    ) {
        String composition = places.stream()
                .filter(place -> !"HOTEL".equalsIgnoreCase(
                        place.getCategory()
                ))
                .sorted(Comparator
                        .comparing(OptimizedPlaceDto::getVisitDate)
                        .thenComparing(OptimizedPlaceDto::getPlaceId))
                .map(place -> place.getVisitDate()
                        + ":" + place.getPlaceId())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return transportMode.name() + ":" + composition;
    }

    /**
     * 이전 추천 키가 구버전 fallback 표시나 숙소 ID를 포함하더라도 일반 장소
     * 구성이 완전히 같으면 동일 코스로 판단한다.
     */
    private boolean isExcludedRecommendationComposition(
            String ordinaryRecommendationKey,
            Set<String> excludedRecommendationKeys,
            List<PlaceCandidateDto> hotelCandidates
    ) {
        Set<Long> hotelIds = hotelCandidates.stream()
                .map(PlaceCandidateDto::getPlaceId)
                .collect(java.util.stream.Collectors.toSet());
        String normalizedCurrent =
                normalizeRecommendationCompositionKey(
                        ordinaryRecommendationKey,
                        hotelIds
                );
        return excludedRecommendationKeys.stream()
                .map(key -> normalizeRecommendationCompositionKey(
                        key,
                        hotelIds
                ))
                .anyMatch(normalizedCurrent::equals);
    }

    private String normalizeRecommendationCompositionKey(
            String recommendationKey,
            Set<Long> hotelIds
    ) {
        if (recommendationKey == null
                || recommendationKey.isBlank()) {
            return "";
        }

        int modeSeparator = recommendationKey.indexOf(':');
        if (modeSeparator < 0) {
            return recommendationKey.trim();
        }
        String modePrefix = recommendationKey.substring(
                0,
                modeSeparator + 1
        );
        String composition = recommendationKey.substring(
                modeSeparator + 1
        );

        if (composition.startsWith("FALLBACK_")) {
            int fallbackSeparator = composition.indexOf(':');
            if (fallbackSeparator >= 0
                    && fallbackSeparator < composition.length() - 1) {
                composition = composition.substring(
                        fallbackSeparator + 1
                );
            }
        }

        List<String> ordinaryTokens = new ArrayList<>();
        for (String rawToken : composition.split(",")) {
            String token = rawToken.trim();
            int placeSeparator = token.lastIndexOf(':');
            if (placeSeparator < 0
                    || placeSeparator == token.length() - 1) {
                continue;
            }
            try {
                Long placeId = Long.parseLong(
                        token.substring(placeSeparator + 1)
                );
                if (!hotelIds.contains(placeId)) {
                    ordinaryTokens.add(token);
                }
            } catch (NumberFormatException ignored) {
                // 형식을 해석할 수 없는 구버전 토큰은 동일성 비교에서 제외한다.
            }
        }
        ordinaryTokens.sort(String::compareTo);
        return modePrefix + String.join(",", ordinaryTokens);
    }

    private Map<LocalDate, Integer> requestedPlaceCountsByDate(
            Map<LocalDate, DailyRouteContext> routeContexts
    ) {
        Map<LocalDate, Integer> counts = new TreeMap<>();
        routeContexts.forEach((date, context) -> counts.put(
                date,
                context.plan().targetPlaceCount()
        ));
        return Map.copyOf(counts);
    }

    /** 최적화 결과를 프론트에서 비교할 수 있는 옵션 한 건으로 변환한다. */
    private CourseOptionResponse toOptionResponse(
            int optionNo,
            OptionStrategy strategy,
            CourseOptimizeResponse optimized,
            LocalTime dailyStartTime,
            String recommendationKey,
            boolean hotelExpected,
            boolean hotelIncluded,
            Map<LocalDate, Integer> requestedPlaceCountsByDate
    ) {
        List<CourseDayResponse> days = toDayResponses(
                optimized.getOptimizedPlaces(),
                dailyStartTime,
                requestedPlaceCountsByDate
        );
        return CourseOptionResponse.builder()
                .optionNo(optionNo)
                .optionType(strategy.name())
                .optionName(strategy.optionName())
                .region(resolveCourseRegion(
                        optimized.getOptimizedPlaces()
                ))
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
                .hotelIncluded(hotelExpected ? hotelIncluded : null)
                .hotelNotice(hotelExpected && !hotelIncluded
                        ? "숙소 도착 30분·다음 DAY 출발 20분 조건을 모두 "
                        + "만족하는 숙소를 찾지 못했습니다. 숙소는 별도로 확인해 주세요."
                        : null)
                .days(days)
                .build();
    }

    /**
     * 최종 응답의 역할명을 실제 결과 지표에 맞춘다. 이동 최소 코스는 세 옵션 중
     * 총 이동시간이 가장 짧아야 하며, 나머지 중 평균 추천 점수가 높은 코스를
     * 취향 집중으로 표시한다. 생성 순서와 화면 순서는 분리한다.
     */
    private void normalizeFinalOptionRoles(
            List<CourseOptionResponse> options
    ) {
        if (options.isEmpty()) {
            return;
        }

        CourseOptionResponse minimumTravel = options.stream()
                .min(Comparator
                        .comparingDouble((CourseOptionResponse option) ->
                                valueOrZero(option.getTotalTravelTimeMinutes()))
                        .thenComparingDouble(option ->
                                valueOrZero(option.getTotalDistanceKm())))
                .orElse(options.get(0));

        List<CourseOptionResponse> remaining = options.stream()
                .filter(option -> option != minimumTravel)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        CourseOptionResponse preference = remaining.stream()
                .max(Comparator.comparingDouble(
                        this::averageRecommendationScore
                ))
                .orElse(null);
        CourseOptionResponse balanced = remaining.stream()
                .filter(option -> option != preference)
                .findFirst()
                .orElse(null);

        applyOptionRole(minimumTravel, OptionStrategy.MIN_DISTANCE);
        if (preference != null) {
            applyOptionRole(preference, OptionStrategy.PREFERENCE);
        }
        if (balanced != null) {
            applyOptionRole(balanced, OptionStrategy.BALANCED);
        }

        options.sort(Comparator.comparingInt(option -> switch (
                OptionStrategy.valueOf(option.getOptionType())
        ) {
            case PREFERENCE -> 0;
            case MIN_DISTANCE -> 1;
            case BALANCED -> 2;
        }));
        for (int index = 0; index < options.size(); index++) {
            options.get(index).setOptionNo(index + 1);
        }
    }

    private double averageRecommendationScore(
            CourseOptionResponse option
    ) {
        return option.getDays().stream()
                .flatMap(day -> day.getPlaces().stream())
                .filter(place -> !"HOTEL".equalsIgnoreCase(
                        place.getCategory()
                ))
                .map(CoursePlaceResponse::getRecommendationScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private void applyOptionRole(
            CourseOptionResponse option,
            OptionStrategy strategy
    ) {
        option.setOptionType(strategy.name());
        option.setOptionName(strategy.optionName());

        String currentTitle = option.getTitle() == null
                ? strategy.optionName()
                : option.getTitle();
        for (OptionStrategy value : OptionStrategy.values()) {
            if (currentTitle.endsWith(value.optionName())) {
                currentTitle = currentTitle.substring(
                        0,
                        currentTitle.length() - value.optionName().length()
                );
                break;
            }
        }
        option.setTitle(currentTitle + strategy.optionName());

        int placeCount = valueOrZero(option.getPlaceCount());
        int dayCount = valueOrZero(option.getDayCount());
        String duration = dayCount <= 1 ? "하루" : dayCount + "일";
        option.setDescription(switch (strategy) {
            case PREFERENCE -> "추천 점수가 높은 " + placeCount
                    + "곳을 중심으로 취향을 가장 진하게 반영한 "
                    + duration + " 코스예요.";
            case MIN_DISTANCE -> "장소 사이 이동을 총 "
                    + round(valueOrZero(option.getTotalDistanceKm()), 1)
                    + "km 동선으로 줄여 부담 없이 이어지는 "
                    + duration + " 코스예요.";
            case BALANCED -> "추천 점수와 이동 시간을 함께 고려해 볼거리와 동선의 균형을 맞춘 "
                    + duration + " 코스예요.";
        });
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
                .region(resolveCandidateDistrict(source))
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

    private String resolveCandidateDistrict(
            PlaceCandidateDto candidate
    ) {
        if (candidate == null) {
            return null;
        }
        String region = normalizeDistrict(candidate.getRegion());
        if (region != null) {
            return region;
        }
        region = normalizeDistrict(candidate.getRoadAddress());
        return region != null
                ? region
                : normalizeDistrict(candidate.getAddress());
    }

    private String resolveOptimizedDistrict(
            OptimizedPlaceDto place
    ) {
        if (place == null) {
            return null;
        }
        String region = normalizeDistrict(place.getRegion());
        if (region != null) {
            return region;
        }
        region = normalizeDistrict(place.getRoadAddress());
        return region != null
                ? region
                : normalizeDistrict(place.getAddress());
    }

    private String normalizeDistrict(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = DISTRICT_PATTERN.matcher(value.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 숙소를 제외한 실제 방문 장소가 가장 많이 속한 구를 코스 대표 지역으로 사용한다. */
    private String resolveCourseRegion(
            List<OptimizedPlaceDto> places
    ) {
        Map<String, RegionUsage> usageByRegion =
                new LinkedHashMap<>();
        for (OptimizedPlaceDto place : places) {
            if (place == null
                    || "HOTEL".equalsIgnoreCase(place.getCategory())) {
                continue;
            }
            String region = resolveOptimizedDistrict(place);
            if (region == null) {
                continue;
            }
            RegionUsage previous = usageByRegion.getOrDefault(
                    region,
                    new RegionUsage(region, 0, 0.0)
            );
            usageByRegion.put(
                    region,
                    new RegionUsage(
                            region,
                            previous.placeCount() + 1,
                            previous.recommendationScore()
                                    + valueOrZero(
                                    place.getRecommendationScore()
                            )
                    )
            );
        }
        return usageByRegion.values().stream()
                .max(Comparator
                        .comparingInt(RegionUsage::placeCount)
                        .thenComparingDouble(
                                RegionUsage::recommendationScore
                        )
                        .thenComparing(
                                RegionUsage::region,
                                Comparator.reverseOrder()
                        ))
                .map(RegionUsage::region)
                .orElse(null);
    }

    /**
     * 선발된 장소는 그대로 유지하고 방문 순서만 자연스럽게 보정한다.
     *
     * <p>R형은 13시 식당 시작을, P형은 관광지 시작 뒤 점심·저녁 식당을 우선한다.
     * 가능한 순서 중 최소 이동시간보다 15분 또는 20%를 초과해 느려지는 경로는
     * 제외한 뒤 음식점·카페 연속 방지와 오후 카페 배치를 함께 평가한다.
     * 도보는 실제값과 예상값 모두 20분 이내 간선만 사용해 하드 제한을 유지한다.</p>
     */
    private CourseOptimizeResponse applyNaturalScheduleFlow(
            CourseOptimizeResponse optimized,
            LocalTime dailyStartTime,
            String scheduleType,
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
                    scheduleType,
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

    /** 최소 동선 허용 범위 안에서 일정형별 시작 장소와 식사·카페 흐름을 고른다. */
    private List<Integer> chooseNaturalRoute(
            List<OptimizedPlaceDto> dailyPlaces,
            NaturalRouteContext context,
            LocalTime dailyStartTime,
            String scheduleType,
            boolean walking,
            Set<Long> usedFirstPlaceIds
    ) {
        List<Integer> allStarts = createOrderedIndexes(dailyPlaces.size());
        List<NaturalRouteEvaluation> evaluations = evaluateNaturalRoutes(
                dailyPlaces,
                context,
                dailyStartTime,
                scheduleType,
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
                        "자연스러운 일정 보정 중 도보 제한을 만족하는 순서를 "
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

        String requiredFirstCategory = "R".equals(scheduleType)
                ? "RESTAURANT"
                : "TOUR";
        List<NaturalRouteEvaluation> preferredStarts =
                allowedEvaluations.stream()
                        .filter(evaluation -> isCategory(
                                dailyPlaces.get(evaluation.route().get(0)),
                                requiredFirstCategory
                        ))
                        .toList();
        if (!preferredStarts.isEmpty()) {
            allowedEvaluations = preferredStarts;
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
            String scheduleType,
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
                    scheduleType,
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
            String scheduleType,
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
                    scheduleType,
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
                    scheduleType,
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
            String scheduleType,
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
                    && legMinutes > WALKING_MAX_MINUTES + EPSILON) {
                return null;
            }
            travelTimeMinutes += legMinutes;
            distanceKm += context.routeMatrix().getDistanceKm(
                    previousMatrixIndex,
                    currentMatrixIndex
            );
        }

        List<Integer> immutableRoute = List.copyOf(route);
        if (hasConsecutiveMealCategory(dailyPlaces, immutableRoute)) {
            return null;
        }
        return new NaturalRouteEvaluation(
                immutableRoute,
                travelTimeMinutes,
                distanceKm,
                calculateNaturalSchedulePenalty(
                        dailyPlaces,
                        context,
                        dailyStartTime,
                        scheduleType,
                        immutableRoute
                ),
                immutableRoute.stream()
                        .map(index -> dailyPlaces.get(index).getPlaceId())
                        .map(String::valueOf)
                        .reduce((left, right) -> left + ">" + right)
                        .orElse("")
        );
    }

    /** 점심·저녁 식당과 오후 카페 규칙을 분 단위의 소프트 페널티로 환산한다. */
    private double calculateNaturalSchedulePenalty(
            List<OptimizedPlaceDto> dailyPlaces,
            NaturalRouteContext context,
            LocalTime dailyStartTime,
            String scheduleType,
            List<Integer> route
    ) {
        double currentMinute = dailyStartTime.getHour() * 60.0
                + dailyStartTime.getMinute();
        Double firstRestaurantArrival = null;
        Double secondRestaurantArrival = null;
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

            String expectedFirstCategory = "R".equals(scheduleType)
                    ? "RESTAURANT"
                    : "TOUR";
            if (position == 0
                    && !isCategory(current, expectedFirstCategory)) {
                penalty += 500.0;
            }
            if (isCategory(current, "RESTAURANT")) {
                if (firstRestaurantArrival == null) {
                    firstRestaurantArrival = currentMinute;
                } else if (secondRestaurantArrival == null) {
                    secondRestaurantArrival = currentMinute;
                }
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
        if (!"R".equals(scheduleType)
                && secondRestaurantArrival != null) {
            if (secondRestaurantArrival < DINNER_START_MINUTE) {
                penalty += (
                        DINNER_START_MINUTE - secondRestaurantArrival
                ) * 1.2;
            } else if (secondRestaurantArrival > DINNER_END_MINUTE) {
                penalty += (
                        secondRestaurantArrival - DINNER_END_MINUTE
                ) * 1.2;
            } else {
                // 허용 시간대 안에서는 18시 30분에 가까울수록 아주 조금 우선한다.
                penalty += Math.abs(
                        secondRestaurantArrival - DINNER_TARGET_MINUTE
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

    /**
     * 식당 또는 카페가 같은 카테고리끼리 연속되는 경로는 후보에서 제외한다.
     *
     * <p>기존에는 120분 소프트 페널티만 부여해 이동시간이 짧으면 식당 두 곳이
     * 연속으로 선택될 수 있었다. 카테고리 배치 규칙은 실제 화면 구성과 직결되므로
     * 자연스러운 경로 탐색 단계에서는 하드 제한으로 적용한다.</p>
     */
    private boolean hasConsecutiveMealCategory(
            List<OptimizedPlaceDto> dailyPlaces,
            List<Integer> route
    ) {
        for (int position = 1; position < route.size(); position++) {
            OptimizedPlaceDto previous = dailyPlaces.get(
                    route.get(position - 1)
            );
            OptimizedPlaceDto current = dailyPlaces.get(
                    route.get(position)
            );
            if (sameCategory(previous, current)
                    && (isCategory(current, "RESTAURANT")
                    || isCategory(current, "CAFE"))) {
                return true;
            }
        }
        return false;
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
                .region(source.getRegion())
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
            LocalTime dailyStartTime,
            Map<LocalDate, Integer> requestedPlaceCountsByDate
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
            int actualPlaceCount = (int) dailyOptimizedPlaces.stream()
                    .filter(place -> !"HOTEL".equalsIgnoreCase(
                            place.getCategory()
                    ))
                    .count();
            int requestedPlaceCount = requestedPlaceCountsByDate.getOrDefault(
                    entry.getKey(),
                    actualPlaceCount
            );
            boolean placeCountAdjusted = actualPlaceCount < requestedPlaceCount;

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
                    .placeCountAdjusted(placeCountAdjusted)
                    .adjustmentReason(placeCountAdjusted
                            ? "INSUFFICIENT_ELIGIBLE_PLACES"
                            : null)
                    .adjustmentNotice(placeCountAdjusted
                            ? "이동시간과 취향 조건을 만족하는 장소가 부족해 이 DAY는 "
                            + actualPlaceCount + "곳으로 조정했어요."
                            : null)
                    .requestedPlaceCount(requestedPlaceCount)
                    .actualPlaceCount(actualPlaceCount)
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
                .region(place.getRegion())
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

    private static final class ActualWalkingLimitException
            extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final Set<LocalDate> violationDates;

        private ActualWalkingLimitException(
                String message,
                Set<LocalDate> violationDates,
                Throwable cause
        ) {
            super(message, cause);
            this.violationDates = Set.copyOf(violationDates);
        }

        private Set<LocalDate> violationDates() {
            return violationDates;
        }
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
            List<PlaceCandidateDto> hotelCandidates,
            String scheduleType,
            List<String> preferredRegions
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
            RouteCostRange routeCostRange,
            Map<Long, List<PlaceCandidateDto>> walkingNeighborCandidates
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

    private record WalkingOptionCandidate(
            OptionStrategy strategy,
            SequentialSelection orderedSelection,
            CourseOptimizeResponse estimated,
            String recommendationKey,
            Map<LocalDate, Set<Long>> ordinaryPlacesByDate,
            Map<LocalDate, Long> firstPlaceIdsByDate,
            String region,
            int previousRecommendationCount,
            int placeCountShortfall,
            int relaxedTierDayCount,
            double averageRecommendationScore
    ) {
    }

    private record WalkingCombinationSelection(
            List<WalkingCandidateCombination> combinations,
            int dailyOverlapLimit
    ) {
    }

    private record WalkingCandidateCombination(
            List<WalkingOptionCandidate> candidates,
            int strategyCoveragePenalty,
            int placeCountShortfall,
            int relaxedTierDayCount,
            int totalOverlap,
            int previousRecommendationCount,
            int regionReuse,
            int firstPlaceReuse,
            double objectivePenalty,
            String signature
    ) {
    }

    private record ResolvedWalkingCandidate(
            WalkingOptionCandidate candidate,
            CourseOptimizeResponse actual
    ) {
    }

    private record ResolvedWalkingResponse(
            CourseRecommendResponse response,
            int placeCountShortfall,
            int dailyOverlapLimit
    ) {
    }

    private record WalkingRepairState(
            SequentialSelection selection,
            int depth
    ) {
    }

    private record WalkingRemovalCandidate(
            Long placeId,
            double travelBurden,
            boolean protectedSingleCategory
    ) {
    }

    private record SelectionConstraints(
            Set<Long> sameCoursePlaceIds,
            Set<Long> hardExcludedPlaceIds,
            boolean forbidSameCourseReuse
    ) {
        boolean isBlocked(Long placeId) {
            return hardExcludedPlaceIds.contains(placeId)
                    || forbidSameCourseReuse
                    && sameCoursePlaceIds.contains(placeId);
        }

        boolean isSameCourseReuse(Long placeId) {
            return sameCoursePlaceIds.contains(placeId);
        }

        SelectionConstraints withForbidSameCourseReuse(boolean forbid) {
            return new SelectionConstraints(
                    sameCoursePlaceIds,
                    hardExcludedPlaceIds,
                    forbid
            );
        }
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
            int sameCourseOverlap,
            int previousRecommendationCount,
            int overlapExcess,
            int totalOverlap,
            String signature
    ) {
    }

    private record PickReuseMetrics(
            int sameCourseOverlap,
            int previousRecommendationCount,
            int overlapExcess,
            int totalOverlap
    ) {
    }

    private record FinalOverlapViolation(
            Set<Long> placeIdsToExclude,
            String reason
    ) {
    }


    private record RouteEstimate(
            double distanceKm,
            double travelTimeMinutes
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
            double maximumArrivalTravelMinutes,
            double averageArrivalTravelMinutes,
            double maximumDepartureTravelMinutes,
            double averageDepartureTravelMinutes,
            boolean estimated
    ) {
        private double maximumTravelMinutes() {
            return Math.max(
                    maximumArrivalTravelMinutes,
                    maximumDepartureTravelMinutes
            );
        }

        private double averageTravelMinutes() {
            return (
                    averageArrivalTravelMinutes
                            + averageDepartureTravelMinutes
            ) / 2.0;
        }
    }

    private record RegionUsage(
            String region,
            int placeCount,
            double recommendationScore
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
