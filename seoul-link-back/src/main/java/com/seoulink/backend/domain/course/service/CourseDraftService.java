package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDraftResponse;
import com.seoulink.backend.domain.course.dto.response.DailyCourseDraftResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationListResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import com.seoulink.backend.domain.place.service.PlaceRecommendationService;
import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import com.seoulink.backend.domain.survey.repository.SurveyResultRepository;
import com.seoulink.backend.domain.survey.repository.TravelSurveyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalTime;

/**
 * 여행 정보와 설문 결과를 바탕으로 날짜별 추천 코스 초안을 생성하는 서비스입니다.
 *
 * 여행 일수와 여행 유형 코드의 P/R 값을 확인하여 하루 목표 장소 수를 결정하고,
 * PlaceRecommendationService에서 받은 장소 후보를 날짜별로 중복 없이 분배합니다.
 *
 * 여기에서 만들어지는 결과는 방문 순서가 정해진 최종 코스가 아니라,
 * 다음 코스 생성 단계에서 사용할 날짜별 후보군입니다.
 */
@Service
@Transactional(readOnly = true)
public class CourseDraftService {

    private static final int MAX_TRAVEL_DAYS = 7;

    // 대표 장소 하나당 받을 대체 후보 수
    private static final int ALTERNATIVE_LIMIT = 3;

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

    /**
     * 설문 번호를 기준으로 날짜별 추천 코스 초안을 생성합니다.
     *
     * @param surveyId 코스 초안을 만들 설문 번호
     * @return 여행 전체 정보와 날짜별 추천 후보 목록
     */
    public CourseDraftResponse createDraft(Long surveyId) {
        validateSurveyId(surveyId);

        // 1. 여행 지역과 날짜가 저장된 설문 정보를 조회
        TravelSurvey survey = travelSurveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 설문 정보를 찾을 수 없습니다. surveyId=" + surveyId
                ));

        // 2. 설문을 통해 완성된 여행 유형 코드를 조회
        SurveyResult surveyResult = surveyResultRepository.findBySurveyId(surveyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 설문의 결과를 찾을 수 없습니다. surveyId=" + surveyId
                ));

        int travelDays = calculateTravelDays(
                survey.getStartDate(),
                survey.getEndDate()
        );

        String travelCode = normalizeTravelCode(
                surveyResult.getTravelCode()
        );

        char scheduleType = travelCode.charAt(4);

        LocalTime dailyStartTime =
                determineDailyStartTime(scheduleType);

        int targetPlaceCount =
                determineTargetPlaceCount(scheduleType);

        int candidatePlaceCount =
                determineCandidatePlaceCount(scheduleType);

        Map<String, Integer> categoryTargets =
                determineCategoryTargets(scheduleType);

        int limitPerCategory =
                determineLimitPerCategory(
                        scheduleType,
                        travelDays
                );

        /*
         * 3. 장소 추천 서비스를 직접 호출
         *
         * 같은 Spring Boot 서버 안에 있으므로
         * /api/places/recommend 주소로 HTTP 요청을 보내지 않고
         * PlaceRecommendationService를 직접 사용합니다.
         */
        PlaceRecommendationListResponse recommendation =
                placeRecommendationService.recommend(
                        travelCode,
                        survey.getRegion(),
                        null,
                        limitPerCategory,
                        ALTERNATIVE_LIMIT
                );

        /*
         * 후보를 카테고리별로 나누어 보관합니다.
         * HOTEL은 현재 여행 코스의 방문 장소에 포함하지 않습니다.
         */
        Map<String, Deque<PlaceRecommendationResponse>> candidatePools =
                createCandidatePools(
                        recommendation.getRecommendedPlaces()
                );

        // 4. 추천 후보를 날짜별로 중복 없이 배분
        List<DailyCourseDraftResponse> dailyPlans =
                distributeCandidatesByDate(
                        survey.getStartDate(),
                        travelDays,
                        targetPlaceCount,
                        candidatePlaceCount,
                        categoryTargets,
                        candidatePools
                );

        return new CourseDraftResponse(
                survey.getSurveyId(),
                surveyResult.getResultId(),
                travelCode,
                survey.getCompanionType(),
                survey.getTransportType(),
                survey.getStartDate(),
                survey.getEndDate(),
                travelDays,
                dailyStartTime,
                dailyPlans
        );
    }

    /**
     * 시작일과 종료일을 모두 포함하여 여행 일수를 계산합니다.
     *
     * 예: 7월 20일부터 7월 22일까지는 3일입니다.
     */
    private int calculateTravelDays(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "여행 시작일과 종료일이 필요합니다"
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "여행 종료일은 시작일보다 빠를 수 없습니다"
            );
        }

        long travelDays =
                ChronoUnit.DAYS.between(startDate, endDate) + 1;

        if (travelDays > MAX_TRAVEL_DAYS) {
            throw new IllegalArgumentException(
                    "여행 기간은 최대 7일까지 가능합니다"
            );
        }

        return (int) travelDays;
    }

    /**
     * 여행 일정 유형에 따라 하루 코스 시작 시간을 결정합니다.
     *
     * P형은 촘촘한 일정이므로 오전 9시에 시작하고,
     * R형은 여유로운 일정이므로 오전 10시 30분에 시작합니다.
     */
    private LocalTime determineDailyStartTime(char scheduleType) {
        return switch (scheduleType) {
            case 'P' -> LocalTime.of(9, 0);
            case 'R' -> LocalTime.of(10, 30);
            default -> throw new IllegalArgumentException(
                    "여행 일정 유형은 P 또는 R이어야 합니다"
            );
        };
    }

    /**
     * P형과 R형에 따라 하루 목표 장소 수를 결정합니다.
     *
     * P형: 계획이 촘촘한 여행이므로 하루 6곳
     * R형: 여유로운 여행이므로 하루 4곳
     */
    private int determineTargetPlaceCount(char scheduleType) {
        return switch (scheduleType) {
            case 'P' -> 6;
            case 'R' -> 4;
            default -> throw new IllegalArgumentException(
                    "여행 일정 유형은 P 또는 R이어야 합니다"
            );
        };
    }

    /**
     * 최종 목표 장소 수보다 넉넉하게 전달할 후보 수를 결정합니다.
     *
     * P형은 날짜별 최대 15개,
     * R형은 날짜별 최대 10개의 후보를 전달합니다.
     *
     * 장소가 부족하면 확보된 후보만 반환합니다.
     */
    private int determineCandidatePlaceCount(char scheduleType) {
        return switch (scheduleType) {
            case 'P' -> 15;
            case 'R' -> 10;
            default -> throw new IllegalArgumentException(
                    "여행 일정 유형은 P 또는 R이어야 합니다"
            );
        };
    }

    /**
     * 여행 일수와 일정 유형에 따라 카테고리별로 요청할 후보 수를 결정합니다.
     *
     * P형은 하루 TOUR 후보가 최대 7개 필요하므로 여행 일수에 7을 곱합니다.
     * R형은 하루 TOUR 후보가 최대 4개 필요하므로 여행 일수에 4를 곱합니다.
     */
    private int determineLimitPerCategory(
            char scheduleType,
            int travelDays
    ) {
        return switch (scheduleType) {
            case 'P' -> 7 * travelDays;
            case 'R' -> 4 * travelDays;
            default -> throw new IllegalArgumentException(
                    "여행 일정 유형은 P 또는 R이어야 합니다"
            );
        };
    }

    /**
     * 일정 유형에 따라 하루에 전달할 카테고리별 후보 개수를 결정합니다.
     *
     * P형은 TOUR 7개, RESTAURANT 4개, CAFE 4개로
     * 하루 최대 15개의 후보를 구성합니다.
     *
     * R형은 TOUR 4개, RESTAURANT 3개, CAFE 3개로
     * 하루 최대 10개의 후보를 구성합니다.
     *
     * HOTEL은 이동 코스가 아니라 숙소로 별도 처리하기 때문에
     * 현재 코스 후보에서는 제외합니다.
     */
    private Map<String, Integer> determineCategoryTargets(
            char scheduleType
    ) {
        Map<String, Integer> targets = new LinkedHashMap<>();

        if (scheduleType == 'P') {
            targets.put(TOUR, 7);
            targets.put(RESTAURANT, 4);
            targets.put(CAFE, 4);
            targets.put(HOTEL, 0);

            return targets;
        }

        if (scheduleType == 'R') {
            targets.put(TOUR, 4);
            targets.put(RESTAURANT, 3);
            targets.put(CAFE, 3);
            targets.put(HOTEL, 0);

            return targets;
        }

        throw new IllegalArgumentException(
                "여행 일정 유형은 P 또는 R이어야 합니다."
        );
    }

    /**
     * 전체 추천 장소를 TOUR, RESTAURANT, CAFE 후보 보관함으로 나눔
     */
    private Map<String, Deque<PlaceRecommendationResponse>>
    createCandidatePools(
            List<PlaceRecommendationResponse> recommendedPlaces
    ) {
        Map<String, Deque<PlaceRecommendationResponse>> pools =
                new LinkedHashMap<>();

        pools.put(TOUR, new ArrayDeque<>());
        pools.put(RESTAURANT, new ArrayDeque<>());
        pools.put(CAFE, new ArrayDeque<>());

        if (recommendedPlaces == null) {
            return pools;
        }

        for (PlaceRecommendationResponse place : recommendedPlaces) {
            if (place.getCategory() == null) {
                continue;
            }

            String category =
                    place.getCategory().trim().toUpperCase();

            Deque<PlaceRecommendationResponse> pool =
                    pools.get(category);

            if (pool != null) {
                pool.addLast(place);
            }
        }

        return pools;
    }

    /**
     * 카테고리별 후보를 날짜마다 나누어 담습니다.
     *
     * 후보를 큐에서 꺼내 사용하기 때문에 한 번 사용한 placeId가
     * 다른 날짜의 기본 후보로 다시 들어가지 않습니다.
     */
    private List<DailyCourseDraftResponse>
    distributeCandidatesByDate(
            LocalDate startDate,
            int travelDays,
            int targetPlaceCount,
            int candidatePlaceCount,
            Map<String, Integer> categoryTargets,
            Map<String, Deque<PlaceRecommendationResponse>> candidatePools
    ) {
        List<List<PlaceRecommendationResponse>> candidatesByDay =
                new ArrayList<>();

        // 날짜별 후보 목록을 먼저 생성
        for (int dayIndex = 0;
             dayIndex < travelDays;
             dayIndex++) {

            candidatesByDay.add(new ArrayList<>());
        }

        /*
         * 1차 배정
         * 모든 날짜에 카테고리별 기본 후보를 먼저 배정한다.
         *
         * P형:
         * TOUR 7, RESTAURANT 4, CAFE 4 = 총 15개
         *
         * R형:
         * TOUR 4, RESTAURANT 3, CAFE 3 = 총 10개
         */
        for (int dayIndex = 0;
             dayIndex < travelDays;
             dayIndex++) {

            List<PlaceRecommendationResponse> dailyCandidates =
                    candidatesByDay.get(dayIndex);

            for (Map.Entry<String, Integer> target
                    : categoryTargets.entrySet()) {

                addCandidates(
                        candidatePools.get(target.getKey()),
                        target.getValue(),
                        dailyCandidates
                );
            }
        }

        /*
         * 2차 배정
         * 카테고리별 기본 후보가 부족한 경우,
         * 남아 있는 다른 카테고리의 후보를 번갈아 추가합니다.
         *
         * P형은 날짜별 최대 15개,
         * R형은 날짜별 최대 10개가 될 때까지 채웁니다.
         */
        for (int dayIndex = 0;
             dayIndex < travelDays;
             dayIndex++) {

            fillExtraCandidates(
                    candidatePools,
                    candidatePlaceCount,
                    candidatesByDay.get(dayIndex),
                    dayIndex
            );
        }

        // 날짜별 응답 DTO 생성
        List<DailyCourseDraftResponse> dailyPlans =
                new ArrayList<>();

        for (int dayIndex = 0;
             dayIndex < travelDays;
             dayIndex++) {

            LocalDate visitDate =
                    startDate.plusDays(dayIndex);

            dailyPlans.add(
                    new DailyCourseDraftResponse(
                            visitDate,
                            targetPlaceCount,
                            new LinkedHashMap<>(categoryTargets),
                            candidatesByDay.get(dayIndex)
                    )
            );
        }

        return dailyPlans;
    }

    /**
     * 특정 카테고리 후보 보관함에서 필요한 개수만큼 장소를 꺼냅니다.
     */
    private void addCandidates(
            Deque<PlaceRecommendationResponse> pool,
            int count,
            List<PlaceRecommendationResponse> destination
    ) {
        if (pool == null) {
            return;
        }

        for (int index = 0;
             index < count && !pool.isEmpty();
             index++) {

            destination.add(pool.removeFirst());
        }
    }

    /**
     * 날짜별 목표 후보 수가 될 때까지 남아 있는 후보를 추가합니다.
     *
     * 시작 카테고리를 날짜마다 변경하여 특정 카테고리에만
     * 추가 후보가 집중되는 현상을 줄입니다.
     */
    private void fillExtraCandidates(
            Map<String, Deque<PlaceRecommendationResponse>> candidatePools,
            int candidatePlaceCount,
            List<PlaceRecommendationResponse> destination,
            int dayIndex
    ) {
        List<String> categories =
                List.of(TOUR, RESTAURANT, CAFE);

        int categoryIndex = dayIndex % categories.size();
        int emptyAttempts = 0;

        while (destination.size() < candidatePlaceCount
                && emptyAttempts < categories.size()) {

            String category =
                    categories.get(
                            categoryIndex % categories.size()
                    );

            Deque<PlaceRecommendationResponse> pool =
                    candidatePools.get(category);

            if (pool != null && !pool.isEmpty()) {
                destination.add(pool.removeFirst());
                emptyAttempts = 0;
            } else {
                emptyAttempts++;
            }

            categoryIndex++;
        }
    }

    private String normalizeTravelCode(String travelCode) {
        if (travelCode == null) {
            throw new IllegalArgumentException(
                    "여행 유형 코드가 필요합니다"
            );
        }

        String normalized = travelCode.trim().toUpperCase();

        if (!normalized.matches("[AH][TM][LB][SD][PR]")) {
            throw new IllegalArgumentException(
                    "여행 유형 코드 형식이 올바르지 않습니다"
            );
        }

        return normalized;
    }

    private void validateSurveyId(Long surveyId) {
        if (surveyId == null || surveyId <= 0) {
            throw new IllegalArgumentException(
                    "올바른 설문 번호가 필요합니다"
            );
        }
    }
}