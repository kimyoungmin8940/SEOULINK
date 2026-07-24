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
 * 앞 날짜에 전달한 장소는 다음 날짜 후보 조회에서 우선 제외하며,
 * 장소가 부족한 경우에만 후보 풀의 fallback 장소를 최소한으로 재사용합니다.</p>
 */
@Service
@Transactional(readOnly = true)
public class CourseDraftService {

    private static final int MAX_TRAVEL_DAYS = 5;
    private static final int HOTEL_CANDIDATE_LIMIT = 6;

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
                dailyTargetPlaceCount
        );

        return new CourseDraftResponse(
                survey.getSurveyId(),
                surveyResult.getResultId(),
                travelCode,
                String.valueOf(scheduleType),
                survey.getCompanionType(),
                survey.getTransportType(),
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
     * 날짜마다 장소 후보 풀을 새로 조회합니다.
     *
     * <p>이전 날짜에 이미 전달한 일반 장소 ID를 제외 목록으로 넘겨 날짜 간 중복을
     * 우선 방지합니다. 데이터가 부족하면 {@link PlaceCandidatePoolResponse}가 제공하는
     * fallback 후보로 부족분만 채웁니다.</p>
     */
    private DraftCandidateResult createDraftCandidates(
            TravelSurvey survey,
            String travelCode,
            char scheduleType,
            int travelDays,
            int desiredTargetPlaceCount
    ) {
        Map<String, Integer> desiredCandidateCounts =
                determineCandidatePoolTargets(scheduleType);
        Set<Long> previouslyAssignedPlaceIds = new LinkedHashSet<>();
        List<DailyCourseDraftResponse> dailyPlans = new ArrayList<>();
        List<PlaceRecommendationResponse> hotelCandidates = List.of();

        for (int dayIndex = 0; dayIndex < travelDays; dayIndex++) {
            PlaceCandidatePoolResponse pool =
                    placeRecommendationService.recommendCandidatePool(
                            travelCode,
                            survey.getRegion(),
                            String.valueOf(scheduleType),
                            survey.getCompanionType(),
                            previouslyAssignedPlaceIds
                    );

            if (dayIndex == 0) {
                hotelCandidates = mergeUniqueCandidates(
                        pool.getHotelCandidates(),
                        pool.getFallbackHotelCandidates(),
                        HOTEL_CANDIDATE_LIMIT,
                        new LinkedHashSet<>()
                );
            }

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

            if (dailyCandidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "추천 가능한 장소 후보가 없습니다. visitDate="
                                + survey.getStartDate().plusDays(dayIndex)
                );
            }

            previouslyAssignedPlaceIds.addAll(dailyCandidateIds);
            int resolvedTargetPlaceCount = Math.min(
                    desiredTargetPlaceCount,
                    dailyCandidates.size()
            );
            LocalDate visitDate = survey.getStartDate().plusDays(dayIndex);

            dailyPlans.add(new DailyCourseDraftResponse(
                    visitDate,
                    resolvedTargetPlaceCount,
                    countCandidatesByCategory(dailyCandidates),
                    dailyCandidates
            ));
        }

        return new DraftCandidateResult(hotelCandidates, dailyPlans);
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

    /** 실제 전달한 후보 수를 추천 API의 categoryTargets로 사용합니다. */
    private Map<String, Integer> countCandidatesByCategory(
            List<PlaceRecommendationResponse> candidates
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(TOUR, 0);
        counts.put(RESTAURANT, 0);
        counts.put(CAFE, 0);
        counts.put(HOTEL, 0);

        for (PlaceRecommendationResponse candidate : candidates) {
            if (candidate == null || candidate.getCategory() == null) {
                continue;
            }
            String category = candidate.getCategory().trim().toUpperCase();
            if (counts.containsKey(category)) {
                counts.put(category, counts.get(category) + 1);
            }
        }
        return counts;
    }

    /** 2번 담당자의 후보 풀 정책과 같은 날짜별 후보 규모입니다. */
    private Map<String, Integer> determineCandidatePoolTargets(char scheduleType) {
        Map<String, Integer> targets = new LinkedHashMap<>();
        if (scheduleType == 'P') {
            targets.put(TOUR, 24);
            targets.put(RESTAURANT, 16);
            targets.put(CAFE, 8);
            return targets;
        }
        if (scheduleType == 'R') {
            targets.put(TOUR, 16);
            targets.put(RESTAURANT, 8);
            targets.put(CAFE, 8);
            return targets;
        }
        throw new IllegalArgumentException("여행 일정 유형은 P 또는 R이어야 합니다.");
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
            case 'P' -> LocalTime.of(9, 0);
            case 'R' -> LocalTime.of(10, 30);
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

    private void validateSurveyId(Long surveyId) {
        if (surveyId == null || surveyId <= 0) {
            throw new IllegalArgumentException("올바른 설문 번호가 필요합니다");
        }
    }

    private record DraftCandidateResult(
            List<PlaceRecommendationResponse> hotelCandidates,
            List<DailyCourseDraftResponse> dailyPlans
    ) {
    }
}
