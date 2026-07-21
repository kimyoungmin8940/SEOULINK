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

    // 팀원의 장소 추천 서비스가 허용하는 카테고리별 최대 후보 수
    private static final int LIMIT_PER_CATEGORY = 20;

    // 대표 장소 하나당 받을 대체 후보 수
    private static final int ALTERNATIVE_LIMIT = 3;

    // 매일 코스를 시작하는 기본 시간
    private static final String DAILY_START_TIME = "10:00";

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

        int targetPlaceCount = determineTargetPlaceCount(
                scheduleType
        );

        int candidatePlaceCount = determineCandidatePlaceCount(
                scheduleType
        );

        Map<String, Integer> categoryTargets =
                determineCategoryTargets(scheduleType);

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
                        LIMIT_PER_CATEGORY,
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
                survey.getStartDate(),
                survey.getEndDate(),
                travelDays,
                DAILY_START_TIME,
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
     * P형: 목표 6곳, 후보 최대 9곳
     * R형: 목표 4곳, 후보 최대 7곳
     */
    private int determineCandidatePlaceCount(char scheduleType) {
        return switch (scheduleType) {
            case 'P' -> 10;
            case 'R' -> 10;
            default -> throw new IllegalArgumentException(
                    "여행 일정 유형은 P 또는 R이어야 합니다"
            );
        };
    }

    /**
     * 일정 유형에 따라 카테고리별 최종 목표 개수를 결정합니다.
     *
     * HOTEL은 이동 코스가 아니라 숙소로 별도 처리할 수 있기 때문에
     * 현재 초안의 목표 카테고리에서는 제외합니다.
     */
    private Map<String, Integer> determineCategoryTargets(
            char scheduleType
    ) {
        Map<String, Integer> targets = new LinkedHashMap<>();

        if (scheduleType == 'P') {
            targets.put(TOUR, 2);
            targets.put(RESTAURANT, 2);
            targets.put(CAFE, 2);
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
         * 모든 날짜에 필수 카테고리 후보를 먼저 배정한다.
         *
         * R형:
         * TOUR 2, RESTAURANT 1, CAFE 1
         *
         * P형:
         * TOUR 2, RESTAURANT 2, CAFE 2
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
         * 모든 날짜의 필수 후보가 확보된 후,
         * 날짜별 후보 수가 7개 또는 9개가 될 때까지 추가한다.
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