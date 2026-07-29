package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDraftResponse;
import com.seoulink.backend.domain.course.dto.response.DailyCourseDraftResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceCandidatePoolResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import com.seoulink.backend.domain.place.service.PlaceRecommendationService;
import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import com.seoulink.backend.domain.survey.repository.SurveyResultRepository;
import com.seoulink.backend.domain.survey.repository.TravelSurveyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 여행 정보와 설문 결과를 바탕으로 날짜별 추천 코스 초안을 생성하는 서비스입니다.
 *
 * <p>설문 메타데이터와 2번 담당자의 장소 후보 풀을 합쳐서
 * {@code /api/courses/recommend}가 바로 사용할 수 있는 {@code dailyPlans}를 만듭니다.
 * 날짜별 후보 풀은 충분히 넓게 유지하고, 실제 DAY 간 중복은 최종 추천 단계에서
 * 앞 DAY에 채택된 장소만 제외해 처리합니다.</p>
 */
@Service
@Transactional(readOnly = true)
public class CourseDraftService {

    private static final int MIN_COURSE_PLACE_COUNT = 3;
    private static final int MAX_PREVIOUSLY_RECOMMENDED_PLACE_IDS = 500;

    private static final int MAX_TRAVEL_DAYS = 5;
    private static final int HOTEL_CANDIDATE_LIMIT = 6;
    private static final int MAX_COURSE_CANDIDATE_TOTAL = 90;

    private static final String TOUR = "TOUR";
    private static final String RESTAURANT = "RESTAURANT";
    private static final String CAFE = "CAFE";
    private static final String HOTEL = "HOTEL";

    private final TravelSurveyRepository travelSurveyRepository;
    private final SurveyResultRepository surveyResultRepository;
    private final PlaceRecommendationService placeRecommendationService;

    public CourseDraftService(
            TravelSurveyRepository travelSurveyRepository,
            SurveyResultRepository surveyResultRepository,
            PlaceRecommendationService placeRecommendationService
    ) {
        this.travelSurveyRepository = travelSurveyRepository;
        this.surveyResultRepository = surveyResultRepository;
        this.placeRecommendationService = placeRecommendationService;
    }

    /** 설문 번호를 기준으로 날짜별 추천 장소 후보 초안을 생성합니다. */
    public CourseDraftResponse createDraft(Long surveyId) {
        return createDraftInternal(surveyId, Set.of());
    }

    /**
     * 다시 추천받기 전용 후보 초안을 생성합니다.
     *
     * <p>직전 화면에 등장한 장소 ID를 후보 조회 서비스에 전달해 DB에서 다음 순위의
     * 장소를 새로 가져옵니다. 이 조회는 장소 DB만 사용하며 ODsay·ORS 같은 외부 경로
     * API를 호출하지 않습니다.</p>
     */
    public CourseDraftResponse createDraftForRecommendAgain(
            Long surveyId,
            Collection<Long> previouslyRecommendedPlaceIds
    ) {
        return createDraftInternal(
                surveyId,
                normalizePreviouslyRecommendedPlaceIds(
                        previouslyRecommendedPlaceIds
                )
        );
    }

    private CourseDraftResponse createDraftInternal(
            Long surveyId,
            Set<Long> previouslyRecommendedPlaceIds
    ) {
        validateSurveyId(surveyId);

        TravelSurvey survey = travelSurveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 설문 정보를 찾을 수 없습니다. surveyId=" + surveyId
                ));

        SurveyResult surveyResult = surveyResultRepository.findBySurveyId(surveyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 설문 결과를 찾을 수 없습니다. surveyId=" + surveyId
                ));

        int travelDays = calculateTravelDays(
                survey.getStartDate(),
                survey.getEndDate()
        );
        String travelCode = normalizeTravelCode(surveyResult.getTravelCode());
        char scheduleType = travelCode.charAt(4);
        LocalTime dailyStartTime = determineDailyStartTime(scheduleType);
        int dailyTargetPlaceCount = determineTargetPlaceCount(scheduleType);
        Map<String, Integer> dailyCategoryTargets =
                determineFinalCategoryTargets(scheduleType);

        DraftCandidateResult candidateResult = createDraftCandidates(
                survey,
                travelCode,
                scheduleType,
                travelDays,
                dailyTargetPlaceCount,
                previouslyRecommendedPlaceIds
        );

        return new CourseDraftResponse(
                survey.getSurveyId(),
                surveyResult.getResultId(),
                travelCode,
                String.valueOf(scheduleType),
                candidateResult.companionType(),
                survey.getTransportType(),
                candidateResult.preferredRegions(),
                survey.getStartDate(),
                survey.getEndDate(),
                travelDays,
                dailyStartTime,
                dailyTargetPlaceCount,
                dailyCategoryTargets,
                candidateResult.hotelCandidates(),
                candidateResult.dailyPlans()
        );
    }

    /**
     * 여행 일수를 반영한 확장 후보 풀을 한 번 조회해 모든 날짜가 공유합니다.
     *
     * <p>동일한 DB 후보를 날짜 수만큼 다시 점수화하지 않습니다. 후보로 조회됐다는
     * 이유만으로 다음 날짜에서 제외하지 않고, 실제 DAY 간 중복은 최종 코스를 만드는
     * {@link CourseRecommendationService}에서 채택 장소 ID 기준으로 처리합니다.</p>
     */
    private DraftCandidateResult createDraftCandidates(
            TravelSurvey survey,
            String travelCode,
            char scheduleType,
            int travelDays,
            int desiredTargetPlaceCount,
            Set<Long> previouslyRecommendedPlaceIds
    ) {
        Map<String, Integer> desiredCandidateCounts =
                determineCandidatePoolTargets(scheduleType, travelDays);
        List<DailyCourseDraftResponse> dailyPlans = new ArrayList<>();
        PlaceCandidatePoolResponse pool =
                placeRecommendationService.recommendCandidatePool(
                        travelCode,
                        survey.getRegion(),
                        String.valueOf(scheduleType),
                        survey.getCompanionType(),
                        previouslyRecommendedPlaceIds,
                        travelDays
                );
        String companionType = pool.getCompanionType();
        List<String> preferredRegions = pool.getPreferredRegions();
        List<PlaceRecommendationResponse> hotelCandidates =
                mergeUniqueCandidates(
                        pool.getHotelCandidates(),
                        pool.getFallbackHotelCandidates(),
                        HOTEL_CANDIDATE_LIMIT,
                        new LinkedHashSet<>()
                );

        for (int dayIndex = 0; dayIndex < travelDays; dayIndex++) {
            List<PlaceRecommendationResponse> dailyCandidates = new ArrayList<>();
            Set<Long> dailyCandidateIds = new LinkedHashSet<>();

            for (Map.Entry<String, Integer> target
                    : desiredCandidateCounts.entrySet()) {
                List<PlaceRecommendationResponse> selected = mergeUniqueCandidates(
                        pool.getCandidatesForCategory(target.getKey()),
                        pool.getFallbackCandidatesForCategory(target.getKey()),
                        target.getValue(),
                        dailyCandidateIds
                );
                dailyCandidates.addAll(selected);
            }

            if (dailyCandidates.size() < MIN_COURSE_PLACE_COUNT) {
                throw new IllegalArgumentException(
                        "코스로 구성할 수 있는 장소가 부족합니다. 최소 "
                                + MIN_COURSE_PLACE_COUNT + "곳이 필요합니다. visitDate="
                                + survey.getStartDate().plusDays(dayIndex)
                                + ", availablePlaces=" + dailyCandidates.size()
                );
            }

            int resolvedTargetPlaceCount = Math.min(
                    desiredTargetPlaceCount,
                    dailyCandidates.size()
            );
            LocalDate visitDate = survey.getStartDate().plusDays(dayIndex);

            dailyPlans.add(new DailyCourseDraftResponse(
                    visitDate,
                    resolvedTargetPlaceCount,
                    determineFinalCategoryTargets(scheduleType),
                    dailyCandidates
            ));
        }

        return new DraftCandidateResult(
                companionType,
                preferredRegions,
                hotelCandidates,
                dailyPlans
        );
    }

    /** 기본 후보를 먼저 사용하고 부족한 수량만 fallback 후보로 채웁니다. */
    private List<PlaceRecommendationResponse> mergeUniqueCandidates(
            List<PlaceRecommendationResponse> primary,
            List<PlaceRecommendationResponse> fallback,
            int limit,
            Set<Long> alreadyUsedIds
    ) {
        List<PlaceRecommendationResponse> merged = new ArrayList<>();
        Set<Long> selectedIds = new LinkedHashSet<>(alreadyUsedIds);

        addUniqueCandidates(primary, limit, merged, selectedIds);
        addUniqueCandidates(fallback, limit, merged, selectedIds);

        alreadyUsedIds.addAll(selectedIds);
        return merged;
    }

    private void addUniqueCandidates(
            List<PlaceRecommendationResponse> source,
            int limit,
            List<PlaceRecommendationResponse> destination,
            Set<Long> selectedIds
    ) {
        if (source == null || source.isEmpty()) {
            return;
        }

        for (PlaceRecommendationResponse candidate : source) {
            if (destination.size() >= limit) {
                return;
            }
            if (candidate == null
                    || candidate.getPlaceId() == null
                    || !selectedIds.add(candidate.getPlaceId())) {
                continue;
            }
            destination.add(candidate);
        }
    }

    /**
     * 하루 기준 P형 30개(15·10·5), R형 20개(10·5·5)를 여행
     * 일수만큼 확장합니다.
     * 장소 데이터가 늘어나도 하루 후보를 고정 32/48개로 자르지 않으며,
     * 전체 탐색 비용은 최대 90개 후보로 제한합니다.
     */
    private Map<String, Integer> determineCandidatePoolTargets(
            char scheduleType,
            int travelDays
    ) {
        Map<String, Integer> finalDailyTargets =
                determineFinalCategoryTargets(scheduleType);
        Map<String, Integer> baseDailyCandidateTargets =
                scheduleType == 'P'
                        ? Map.of(TOUR, 15, RESTAURANT, 10, CAFE, 5)
                        : Map.of(TOUR, 10, RESTAURANT, 5, CAFE, 5);
        Map<String, Integer> rawTargets = new LinkedHashMap<>();
        for (String category : List.of(TOUR, RESTAURANT, CAFE)) {
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

        Map<String, Integer> targets = new LinkedHashMap<>();
        for (String category : List.of(TOUR, RESTAURANT, CAFE)) {
            targets.put(
                    category,
                    Math.max(
                            finalDailyTargets.getOrDefault(category, 0),
                            (int) Math.floor(
                                    rawTargets.get(category) * scale
                            )
                    )
            );
        }
        return targets;
    }

    /** 화면 안내용 최종 장소 비율입니다. */
    private Map<String, Integer> determineFinalCategoryTargets(char scheduleType) {
        Map<String, Integer> targets = new LinkedHashMap<>();
        if (scheduleType == 'P') {
            targets.put(TOUR, 3);
            targets.put(RESTAURANT, 2);
            targets.put(CAFE, 1);
            targets.put(HOTEL, 0);
            return targets;
        }
        if (scheduleType == 'R') {
            targets.put(TOUR, 2);
            targets.put(RESTAURANT, 1);
            targets.put(CAFE, 1);
            targets.put(HOTEL, 0);
            return targets;
        }
        throw new IllegalArgumentException("여행 일정 유형은 P 또는 R이어야 합니다.");
    }

    private int calculateTravelDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("여행 시작일과 종료일이 필요합니다");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("여행 종료일은 시작일보다 빠를 수 없습니다");
        }

        long travelDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (travelDays > MAX_TRAVEL_DAYS) {
            throw new IllegalArgumentException("여행 기간은 최대 5일까지 가능합니다");
        }
        return (int) travelDays;
    }

    private LocalTime determineDailyStartTime(char scheduleType) {
        return switch (scheduleType) {
            case 'P' -> LocalTime.of(11, 0);
            case 'R' -> LocalTime.of(13, 0);
            default -> throw new IllegalArgumentException(
                    "여행 일정 유형은 P 또는 R이어야 합니다"
            );
        };
    }

    private int determineTargetPlaceCount(char scheduleType) {
        return switch (scheduleType) {
            case 'P' -> 6;
            case 'R' -> 4;
            default -> throw new IllegalArgumentException(
                    "여행 일정 유형은 P 또는 R이어야 합니다"
            );
        };
    }

    private String normalizeTravelCode(String travelCode) {
        if (travelCode == null) {
            throw new IllegalArgumentException("여행 유형 코드가 필요합니다");
        }
        String normalized = travelCode.trim().toUpperCase();
        if (!normalized.matches("[AH][TM][LB][SD][PR]")) {
            throw new IllegalArgumentException("여행 유형 코드 형식이 올바르지 않습니다");
        }
        return normalized;
    }


    private Set<Long> normalizePreviouslyRecommendedPlaceIds(
            Collection<Long> source
    ) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        if (source.size() > MAX_PREVIOUSLY_RECOMMENDED_PLACE_IDS) {
            throw new IllegalArgumentException(
                    "previouslyRecommendedPlaceIds는 최대 "
                            + MAX_PREVIOUSLY_RECOMMENDED_PLACE_IDS
                            + "개까지 전달할 수 있습니다."
            );
        }

        Set<Long> normalized = new LinkedHashSet<>();
        int index = 0;
        for (Long placeId : source) {
            if (placeId == null || placeId <= 0) {
                throw new IllegalArgumentException(
                        "previouslyRecommendedPlaceIds[" + index
                                + "]는 1 이상의 장소 ID여야 합니다."
                );
            }
            normalized.add(placeId);
            index++;
        }
        return Collections.unmodifiableSet(normalized);
    }

    private void validateSurveyId(Long surveyId) {
        if (surveyId == null || surveyId <= 0) {
            throw new IllegalArgumentException("올바른 설문 번호가 필요합니다");
        }
    }

    private record DraftCandidateResult(
            String companionType,
            List<String> preferredRegions,
            List<PlaceRecommendationResponse> hotelCandidates,
            List<DailyCourseDraftResponse> dailyPlans
    ) {
    }
}
