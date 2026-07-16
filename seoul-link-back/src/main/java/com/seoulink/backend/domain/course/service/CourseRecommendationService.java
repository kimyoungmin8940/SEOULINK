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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** 날짜별 후보 풀에서 서로 다른 세 가지 추천 코스를 생성한다. */
@Service
public class CourseRecommendationService {

    private static final List<String> SUPPORTED_CATEGORIES = List.of(
            "TOUR",
            "RESTAURANT",
            "CAFE",
            "HOTEL"
    );
    private static final int OPTION_COUNT = 3;
    private static final int MAX_DAILY_SELECTIONS = 10_000;
    private static final int MAX_GLOBAL_SELECTIONS = 10_000;
    private static final double FALLBACK_WALKING_SPEED_KM_PER_HOUR = 4.5;

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
     * 최종 요청 JSON의 날짜별 후보 풀에서 취향 우선·이동 최소·균형 코스를 만든다.
     *
     * <p>각 날짜마다 {@code targetPlaceCount}와 {@code categoryTargets}를 정확히 지키는
     * 가능한 장소 조합을 만든 뒤 세 전략으로 순위를 계산한다. 후보 조합이 세 개 이상이면
     * 서로 다른 조합을 우선 반환하며, 후보가 부족해 세 조합을 만들 수 없는 경우에는
     * 전략별 최선 결과가 일부 겹칠 수 있다. 추천 결과는 저장하지 않고 사용자가 선택한
     * 한 코스만 기존 {@code POST /api/courses}로 저장한다.</p>
     */
    public CourseRecommendResponse recommend(CourseRecommendRequest request) {
        ValidatedRecommendation validated = validateAndPrepare(request);
        List<List<DailySelection>> selectionsByDay = new ArrayList<>();

        for (ValidatedDailyPlan dailyPlan : validated.dailyPlans()) {
            selectionsByDay.add(createDailySelections(dailyPlan));
        }

        List<GlobalSelection> globalSelections = createGlobalSelections(
                selectionsByDay
        );
        if (globalSelections.isEmpty()) {
            throw new IllegalArgumentException("조건에 맞는 추천 코스 조합을 만들 수 없습니다.");
        }

        List<CourseOptionResponse> courseOptions = new ArrayList<>();
        Set<String> usedSignatures = new HashSet<>();
        int optionNo = 1;

        for (OptionStrategy strategy : OptionStrategy.values()) {
            GlobalSelection selected = selectGlobalOption(
                    globalSelections,
                    strategy,
                    usedSignatures
            );
            usedSignatures.add(selected.signature());

            CourseOptimizeResponse optimized = courseOptimizationService.optimize(
                    CourseOptimizeRequest.builder()
                            .placeCandidates(selected.placeCandidates())
                            .build()
            );
            courseOptions.add(toOptionResponse(
                    optionNo++,
                    strategy,
                    optimized,
                    request.getDailyStartTime()
            ));
        }

        return CourseRecommendResponse.builder()
                .resultId(request.getResultId())
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
        if (request.getDailyStartTime() == null) {
            throw new IllegalArgumentException("일정 시작 시각은 필수입니다.");
        }
        if (request.getDailyPlans() == null || request.getDailyPlans().isEmpty()) {
            throw new IllegalArgumentException("날짜별 일정이 한 개 이상 필요합니다.");
        }

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

        return new ValidatedRecommendation(
                new ArrayList<>(plansByDate.values())
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
        if (categoryTargetSum != targetPlaceCount) {
            throw new IllegalArgumentException(
                    "categoryTargets 합계는 targetPlaceCount와 같아야 합니다. visitDate="
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

        return new ValidatedDailyPlan(
                visitDate,
                targetPlaceCount,
                categoryTargets,
                candidates
        );
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

    /** 카테고리 목표를 정확히 만족하는 하루 장소 조합을 모두 만든다. */
    private List<DailySelection> createDailySelections(
            ValidatedDailyPlan dailyPlan
    ) {
        List<List<PlaceCandidateDto>> combinations = new ArrayList<>();
        buildCategorySelections(
                dailyPlan,
                0,
                new ArrayList<>(),
                combinations
        );
        if (combinations.isEmpty()) {
            throw new IllegalArgumentException(
                    "카테고리 목표를 만족하는 장소 조합이 없습니다. visitDate="
                            + dailyPlan.visitDate()
            );
        }

        Map<String, DailySelection> uniqueSelections = new LinkedHashMap<>();
        for (List<PlaceCandidateDto> combination : combinations) {
            RouteEstimate routeEstimate = estimateRoute(combination);
            double recommendationScore = combination.stream()
                    .mapToDouble(candidate -> valueOrZero(
                            candidate.getRecommendationScore()
                    ))
                    .sum();
            String signature = createCompositionSignature(combination);
            DailySelection selection = new DailySelection(
                    List.copyOf(combination),
                    recommendationScore,
                    routeEstimate.travelTimeMinutes(),
                    routeEstimate.distanceKm(),
                    signature
            );
            uniqueSelections.putIfAbsent(signature, selection);
        }
        return new ArrayList<>(uniqueSelections.values());
    }

    /** 기본 카테고리 순서대로 필요한 개수의 조합을 생성한다. */
    private void buildCategorySelections(
            ValidatedDailyPlan dailyPlan,
            int categoryIndex,
            List<PlaceCandidateDto> selected,
            List<List<PlaceCandidateDto>> output
    ) {
        if (output.size() >= MAX_DAILY_SELECTIONS) {
            return;
        }
        if (categoryIndex == SUPPORTED_CATEGORIES.size()) {
            if (selected.size() == dailyPlan.targetPlaceCount()) {
                output.add(new ArrayList<>(selected));
            }
            return;
        }

        String category = SUPPORTED_CATEGORIES.get(categoryIndex);
        int required = dailyPlan.categoryTargets().getOrDefault(category, 0);
        if (required == 0) {
            buildCategorySelections(
                    dailyPlan,
                    categoryIndex + 1,
                    selected,
                    output
            );
            return;
        }

        List<PlaceCandidateDto> categoryCandidates = dailyPlan.placeCandidates()
                .stream()
                .filter(candidate -> category.equals(candidate.getCategory()))
                .sorted(Comparator.comparing(PlaceCandidateDto::getPlaceId))
                .toList();
        List<List<PlaceCandidateDto>> categoryCombinations = new ArrayList<>();
        chooseCandidates(
                categoryCandidates,
                required,
                0,
                new ArrayList<>(),
                categoryCombinations
        );

        for (List<PlaceCandidateDto> categoryCombination : categoryCombinations) {
            selected.addAll(categoryCombination);
            buildCategorySelections(
                    dailyPlan,
                    categoryIndex + 1,
                    selected,
                    output
            );
            selected.subList(
                    selected.size() - categoryCombination.size(),
                    selected.size()
            ).clear();
            if (output.size() >= MAX_DAILY_SELECTIONS) {
                return;
            }
        }
    }

    /** 후보 목록에서 required개를 뽑는 조합을 만든다. */
    private void chooseCandidates(
            List<PlaceCandidateDto> candidates,
            int required,
            int startIndex,
            List<PlaceCandidateDto> current,
            List<List<PlaceCandidateDto>> output
    ) {
        if (current.size() == required) {
            output.add(new ArrayList<>(current));
            return;
        }

        int remainingNeeded = required - current.size();
        for (int index = startIndex;
                index <= candidates.size() - remainingNeeded;
                index++) {
            current.add(candidates.get(index));
            chooseCandidates(
                    candidates,
                    required,
                    index + 1,
                    current,
                    output
            );
            current.remove(current.size() - 1);
        }
    }

    /** 하루별 조합을 합쳐 전체 여행 코스 후보를 만든다. */
    private List<GlobalSelection> createGlobalSelections(
            List<List<DailySelection>> selectionsByDay
    ) {
        List<GlobalSelection> output = new ArrayList<>();
        buildGlobalSelections(
                selectionsByDay,
                0,
                new ArrayList<>(),
                output
        );
        return output;
    }

    private void buildGlobalSelections(
            List<List<DailySelection>> selectionsByDay,
            int dayIndex,
            List<DailySelection> selectedDays,
            List<GlobalSelection> output
    ) {
        if (output.size() >= MAX_GLOBAL_SELECTIONS) {
            return;
        }
        if (dayIndex == selectionsByDay.size()) {
            List<PlaceCandidateDto> placeCandidates = new ArrayList<>();
            double recommendationScore = 0.0;
            double travelTimeMinutes = 0.0;
            double distanceKm = 0.0;
            StringBuilder signature = new StringBuilder();

            for (DailySelection dailySelection : selectedDays) {
                placeCandidates.addAll(dailySelection.placeCandidates());
                recommendationScore += dailySelection.recommendationScore();
                travelTimeMinutes += dailySelection.travelTimeMinutes();
                distanceKm += dailySelection.distanceKm();
                if (!signature.isEmpty()) {
                    signature.append('|');
                }
                signature.append(dailySelection.signature());
            }

            output.add(new GlobalSelection(
                    List.copyOf(placeCandidates),
                    recommendationScore,
                    travelTimeMinutes,
                    distanceKm,
                    signature.toString()
            ));
            return;
        }

        for (DailySelection selection : selectionsByDay.get(dayIndex)) {
            selectedDays.add(selection);
            buildGlobalSelections(
                    selectionsByDay,
                    dayIndex + 1,
                    selectedDays,
                    output
            );
            selectedDays.remove(selectedDays.size() - 1);
            if (output.size() >= MAX_GLOBAL_SELECTIONS) {
                return;
            }
        }
    }

    /** 전략별 순위를 계산하고 아직 사용하지 않은 조합을 우선 선택한다. */
    private GlobalSelection selectGlobalOption(
            List<GlobalSelection> selections,
            OptionStrategy strategy,
            Set<String> usedSignatures
    ) {
        List<GlobalSelection> ranked = rankGlobalSelections(
                selections,
                strategy
        );
        for (GlobalSelection selection : ranked) {
            if (!usedSignatures.contains(selection.signature())) {
                return selection;
            }
        }
        return ranked.get(0);
    }

    private List<GlobalSelection> rankGlobalSelections(
            List<GlobalSelection> selections,
            OptionStrategy strategy
    ) {
        List<GlobalSelection> ranked = new ArrayList<>(selections);
        Comparator<GlobalSelection> comparator;

        if (strategy == OptionStrategy.PREFERENCE) {
            comparator = Comparator
                    .comparingDouble(GlobalSelection::recommendationScore)
                    .reversed()
                    .thenComparingDouble(GlobalSelection::travelTimeMinutes)
                    .thenComparingDouble(GlobalSelection::distanceKm)
                    .thenComparing(GlobalSelection::signature);
        } else if (strategy == OptionStrategy.MIN_DISTANCE) {
            comparator = Comparator
                    .comparingDouble(GlobalSelection::travelTimeMinutes)
                    .thenComparingDouble(GlobalSelection::distanceKm)
                    .thenComparing(
                            Comparator.comparingDouble(
                                    GlobalSelection::recommendationScore
                            ).reversed()
                    )
                    .thenComparing(GlobalSelection::signature);
        } else {
            MetricRange range = MetricRange.from(selections);
            comparator = Comparator
                    .comparingDouble(
                            (GlobalSelection selection) -> balancedUtility(
                                    selection,
                                    range
                            )
                    )
                    .reversed()
                    .thenComparing(
                            Comparator.comparingDouble(
                                    GlobalSelection::recommendationScore
                            ).reversed()
                    )
                    .thenComparingDouble(GlobalSelection::travelTimeMinutes)
                    .thenComparing(GlobalSelection::signature);
        }

        ranked.sort(comparator);
        return ranked;
    }

    /** 추천 점수 50%, 이동시간 30%, 거리 20% 비율의 정규화 균형 점수이다. */
    private double balancedUtility(
            GlobalSelection selection,
            MetricRange range
    ) {
        double preference = normalizeHigher(
                selection.recommendationScore(),
                range.minRecommendationScore(),
                range.maxRecommendationScore()
        );
        double travelEfficiency = normalizeLower(
                selection.travelTimeMinutes(),
                range.minTravelTimeMinutes(),
                range.maxTravelTimeMinutes()
        );
        double distanceEfficiency = normalizeLower(
                selection.distanceKm(),
                range.minDistanceKm(),
                range.maxDistanceKm()
        );
        return preference * 0.50
                + travelEfficiency * 0.30
                + distanceEfficiency * 0.20;
    }

    /** 외부 경로 API 호출 없이 후보 조합의 직선거리 기반 이동 비용을 빠르게 추정한다. */
    private RouteEstimate estimateRoute(List<PlaceCandidateDto> candidates) {
        if (candidates.size() < 2) {
            return new RouteEstimate(0.0, 0.0);
        }

        List<PlaceCandidateDto> remaining = new ArrayList<>(candidates);
        PlaceCandidateDto current = remaining.stream()
                .max(Comparator
                        .comparingDouble(
                                (PlaceCandidateDto candidate) -> valueOrZero(
                                        candidate.getRecommendationScore()
                                )
                        )
                        .thenComparing(
                                candidate -> candidate.getPlaceId() == null
                                        ? Long.MAX_VALUE
                                        : -candidate.getPlaceId()
                        ))
                .orElseThrow();
        remaining.remove(current);

        double totalDistanceKm = 0.0;
        while (!remaining.isEmpty()) {
            PlaceCandidateDto previous = current;
            current = remaining.stream()
                    .min(Comparator
                            .comparingDouble((PlaceCandidateDto candidate) -> distanceService.calculateDistanceKm(
                                    previous.getLatitude(),
                                    previous.getLongitude(),
                                    candidate.getLatitude(),
                                    candidate.getLongitude()
                            ))
                            .thenComparing(PlaceCandidateDto::getPlaceId))
                    .orElseThrow();
            totalDistanceKm += distanceService.calculateDistanceKm(
                    previous.getLatitude(),
                    previous.getLongitude(),
                    current.getLatitude(),
                    current.getLongitude()
            );
            remaining.remove(current);
        }

        double travelTimeMinutes = totalDistanceKm
                / FALLBACK_WALKING_SPEED_KM_PER_HOUR * 60.0;
        return new RouteEstimate(totalDistanceKm, travelTimeMinutes);
    }

    /** 조합의 장소 ID를 정렬해 방문 순서와 무관한 고유 서명을 만든다. */
    private String createCompositionSignature(
            List<PlaceCandidateDto> candidates
    ) {
        return candidates.stream()
                .sorted(Comparator.comparing(PlaceCandidateDto::getPlaceId))
                .map(candidate -> candidate.getVisitDate()
                        + ":" + candidate.getPlaceId())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    /** 최적화 결과를 프론트에서 비교할 수 있는 옵션 한 건으로 변환한다. */
    private CourseOptionResponse toOptionResponse(
            int optionNo,
            OptionStrategy strategy,
            CourseOptimizeResponse optimized,
            LocalTime dailyStartTime
    ) {
        List<CourseDayResponse> days = toDayResponses(
                optimized.getOptimizedPlaces(),
                dailyStartTime
        );
        return CourseOptionResponse.builder()
                .optionNo(optionNo)
                .optionType(strategy.name())
                .optionName(strategy.optionName())
                .placeCount(optimized.getOptimizedPlaces().size())
                .dayCount(days.size())
                .totalDistanceKm(round(optimized.getTotalDistanceKm(), 3))
                .totalTravelTimeMinutes(round(
                        optimized.getTotalTravelTimeMinutes(),
                        2
                ))
                .totalVisitTimeMinutes(optimized.getTotalVisitTimeMinutes())
                .totalCourseTimeMinutes(round(
                        optimized.getTotalCourseTimeMinutes(),
                        2
                ))
                .days(days)
                .build();
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
        if (includeAlternatives && source.getAlternativeCandidates() != null) {
            for (PlaceCandidateDto alternative : source.getAlternativeCandidates()) {
                alternatives.add(copyCandidateWithDate(
                        alternative,
                        visitDate,
                        false
                ));
            }
        }

        return PlaceCandidateDto.builder()
                .placeId(source.getPlaceId())
                .placeName(source.getPlaceName())
                .category(normalizeCategory(source.getCategory()))
                .recommendationScore(source.getRecommendationScore())
                .latitude(source.getLatitude())
                .longitude(source.getLongitude())
                .visitDate(visitDate)
                .themePalaceCultureYn(normalizeYn(source.getThemePalaceCultureYn(), "themePalaceCultureYn"))
                .themeNatureHangangYn(normalizeYn(source.getThemeNatureHangangYn(), "themeNatureHangangYn"))
                .themeDateYn(normalizeYn(source.getThemeDateYn(), "themeDateYn"))
                .themeFoodTourYn(normalizeYn(source.getThemeFoodTourYn(), "themeFoodTourYn"))
                .themeCafeTourYn(normalizeYn(source.getThemeCafeTourYn(), "themeCafeTourYn"))
                .themeShoppingHotplaceYn(normalizeYn(source.getThemeShoppingHotplaceYn(), "themeShoppingHotplaceYn"))
                .themeNightViewYn(normalizeYn(source.getThemeNightViewYn(), "themeNightViewYn"))
                .themeHotelStayYn(normalizeYn(source.getThemeHotelStayYn(), "themeHotelStayYn"))
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
        Map<LocalDate, List<OptimizedPlaceDto>> placesByDate = new TreeMap<>();
        for (OptimizedPlaceDto place : optimizedPlaces) {
            placesByDate
                    .computeIfAbsent(place.getVisitDate(), ignored -> new ArrayList<>())
                    .add(place);
        }

        List<CourseDayResponse> days = new ArrayList<>();
        int dayNo = 1;
        for (Map.Entry<LocalDate, List<OptimizedPlaceDto>> entry
                : placesByDate.entrySet()) {
            List<OptimizedPlaceDto> dailyOptimizedPlaces = entry.getValue().stream()
                    .sorted(Comparator.comparing(OptimizedPlaceDto::getVisitOrder))
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
            double dailyTravelTimeMinutes = dailyOptimizedPlaces.stream()
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
                            dailyVisitTimeMinutes + dailyTravelTimeMinutes,
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
            currentTime = currentTime.plusMinutes(Math.round(valueOrZero(
                    place.getTravelTimeFromPreviousMinutes()
            )));
            responses.add(toPlaceResponse(
                    place,
                    currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            ));
            currentTime = currentTime.plusMinutes(valueOrZero(
                    place.getExpectedVisitMinutes()
            ));
        }
        return responses;
    }

    /** 추천 직후 화면에 필요한 표시값과 최적화 계산값을 응답 DTO로 변환한다. */
    private CoursePlaceResponse toPlaceResponse(
            OptimizedPlaceDto place,
            String visitTime
    ) {
        return CoursePlaceResponse.builder()
                .placeId(place.getPlaceId())
                .placeName(place.getPlaceName())
                .category(place.getCategory())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .recommendationScore(place.getRecommendationScore())
                .themePalaceCultureYn(place.getThemePalaceCultureYn())
                .themeNatureHangangYn(place.getThemeNatureHangangYn())
                .themeDateYn(place.getThemeDateYn())
                .themeFoodTourYn(place.getThemeFoodTourYn())
                .themeCafeTourYn(place.getThemeCafeTourYn())
                .themeShoppingHotplaceYn(place.getThemeShoppingHotplaceYn())
                .themeNightViewYn(place.getThemeNightViewYn())
                .themeHotelStayYn(place.getThemeHotelStayYn())
                .visitOrder(place.getVisitOrder())
                .visitTime(visitTime)
                .expectedVisitMinutes(place.getExpectedVisitMinutes())
                .distanceFromPreviousKm(place.getDistanceFromPreviousKm())
                .travelTimeFromPreviousMinutes(
                        place.getTravelTimeFromPreviousMinutes()
                )
                .build();
    }

    /** 테마 여부는 누락 시 N으로 두고 Y/N 외의 값은 요청 오류로 처리한다. */
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

    private double normalizeHigher(double value, double min, double max) {
        if (Math.abs(max - min) < 0.000000001) {
            return 1.0;
        }
        return (value - min) / (max - min);
    }

    private double normalizeLower(double value, double min, double max) {
        return 1.0 - normalizeHigher(value, min, max);
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
            List<ValidatedDailyPlan> dailyPlans
    ) {
    }

    private record ValidatedDailyPlan(
            LocalDate visitDate,
            int targetPlaceCount,
            Map<String, Integer> categoryTargets,
            List<PlaceCandidateDto> placeCandidates
    ) {
    }

    private record DailySelection(
            List<PlaceCandidateDto> placeCandidates,
            double recommendationScore,
            double travelTimeMinutes,
            double distanceKm,
            String signature
    ) {
    }

    private record GlobalSelection(
            List<PlaceCandidateDto> placeCandidates,
            double recommendationScore,
            double travelTimeMinutes,
            double distanceKm,
            String signature
    ) {
    }

    private record RouteEstimate(
            double distanceKm,
            double travelTimeMinutes
    ) {
    }

    private record MetricRange(
            double minRecommendationScore,
            double maxRecommendationScore,
            double minTravelTimeMinutes,
            double maxTravelTimeMinutes,
            double minDistanceKm,
            double maxDistanceKm
    ) {
        static MetricRange from(List<GlobalSelection> selections) {
            return new MetricRange(
                    selections.stream()
                            .mapToDouble(GlobalSelection::recommendationScore)
                            .min()
                            .orElse(0.0),
                    selections.stream()
                            .mapToDouble(GlobalSelection::recommendationScore)
                            .max()
                            .orElse(0.0),
                    selections.stream()
                            .mapToDouble(GlobalSelection::travelTimeMinutes)
                            .min()
                            .orElse(0.0),
                    selections.stream()
                            .mapToDouble(GlobalSelection::travelTimeMinutes)
                            .max()
                            .orElse(0.0),
                    selections.stream()
                            .mapToDouble(GlobalSelection::distanceKm)
                            .min()
                            .orElse(0.0),
                    selections.stream()
                            .mapToDouble(GlobalSelection::distanceKm)
                            .max()
                            .orElse(0.0)
            );
        }
    }
}
