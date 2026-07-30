package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.CourseSavePlaceDto;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptionResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import com.seoulink.backend.domain.survey.repository.SurveyResultRepository;
import com.seoulink.backend.domain.survey.repository.TravelSurveyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 추천 API가 실제로 사용자에게 반환한 모든 코스를 추천 이력으로 남긴다.
 *
 * <p>추천 이력과 내 코스는 TRAVEL_COURSES.IS_SAVED로 구분한다. 생성 직후
 * 추천 옵션은 N이고, 사용자가 저장을 누르면 CourseSaveService가 같은 행을
 * Y로 전환한다.</p>
 */
@Service
public class CourseRecommendationHistoryService {

    private static final Logger log = LoggerFactory.getLogger(
            CourseRecommendationHistoryService.class
    );

    private final SurveyResultRepository surveyResultRepository;
    private final TravelSurveyRepository travelSurveyRepository;
    private final CourseSaveService courseSaveService;

    public CourseRecommendationHistoryService(
            SurveyResultRepository surveyResultRepository,
            TravelSurveyRepository travelSurveyRepository,
            CourseSaveService courseSaveService
    ) {
        this.surveyResultRepository = surveyResultRepository;
        this.travelSurveyRepository = travelSurveyRepository;
        this.courseSaveService = courseSaveService;
    }

    /**
     * 로그인 회원의 추천 결과만 이력으로 기록한다.
     *
     * <p>비회원 설문은 회원 ID가 없으므로 이 단계에서는 건너뛰며, 로그인
     * 회원의 최초 추천 3개와 재추천 3개는 각각 별도 호출에서 누적된다.</p>
     */
    @Transactional
    public void record(
            CourseRecommendRequest request,
            CourseRecommendResponse response
    ) {
        if (response == null) {
            return;
        }

        Long resultId = response.getResultId() != null
                ? response.getResultId()
                : request == null ? null : request.getResultId();
        Long memberId = resolveMemberId(request, resultId);
        TransportMode transportMode = response.getTransportMode() != null
                ? response.getTransportMode()
                : request == null ? null : request.getTransportMode();

        if (memberId == null || resultId == null || transportMode == null) {
            log.debug(
                    "회원 추천 이력 저장을 건너뜁니다: memberId={}, resultId={}, "
                            + "transportMode={}",
                    memberId,
                    resultId,
                    transportMode
            );
            return;
        }

        List<CourseOptionResponse> historyOptions = response.getCourseOptions()
                == null
                ? List.of()
                : response.getCourseOptions().stream()
                .filter(java.util.Objects::nonNull)
                .filter(option -> option.getDays() != null)
                .filter(option -> option.getDays().stream().anyMatch(day ->
                        day != null
                                && day.getPlaces() != null
                                && !day.getPlaces().isEmpty()
                ))
                .toList();
        List<CourseSaveRequest> historyRequests = historyOptions.stream()
                .map(option -> toHistoryRequest(
                        memberId,
                        resultId,
                        response,
                        request,
                        option,
                        transportMode
                ))
                .filter(historyRequest -> !historyRequest.getPlaces().isEmpty())
                .toList();

        List<CourseSaveResponse> savedCourses =
                courseSaveService.saveRecommendationHistory(historyRequests);
        if (savedCourses == null) {
            return;
        }

        int mappedCount = Math.min(
                historyOptions.size(),
                savedCourses.size()
        );
        for (int index = 0; index < mappedCount; index++) {
            CourseSaveResponse savedCourse = savedCourses.get(index);
            if (savedCourse != null && savedCourse.getCourseId() != null) {
                historyOptions.get(index).setCourseId(
                        savedCourse.getCourseId()
                );
            }
        }
    }

    /** 브라우저 세션에 남은 기존 추천 응답을 현재 회원 이력으로 복구한다. */
    @Transactional
    public void record(CourseRecommendResponse response) {
        record(response, null);
    }

    /**
     * 브라우저 세션에 남은 추천 응답을 명시한 회원의 이력으로 복구한다.
     *
     * <p>설문이 이미 회원에게 연결돼 있으면 설문 소유자를 우선하고, 게스트
     * 설문처럼 소유자가 비어 있을 때만 전달받은 회원 ID를 사용한다.</p>
     */
    @Transactional
    public void record(CourseRecommendResponse response, Long memberId) {
        if (memberId != null && memberId < 1) {
            throw new IllegalArgumentException(
                    "추천 이력 회원 ID는 1 이상이어야 합니다."
            );
        }

        CourseRecommendRequest request = memberId == null
                ? null
                : CourseRecommendRequest.builder()
                .memberId(memberId)
                .build();
        record(request, response);
    }

    /**
     * 설문 소유 회원을 우선 복원하고, 게스트 설문이면 추천 요청의 로그인
     * 회원 ID를 사용한다.
     */
    private Long resolveMemberId(
            CourseRecommendRequest request,
            Long resultId
    ) {
        Long surveyId = null;

        if (resultId != null) {
            surveyId = surveyResultRepository.findById(resultId)
                    .map(SurveyResult::getSurveyId)
                    .orElse(null);
        }
        if (surveyId == null) {
            surveyId = request == null ? null : request.getSurveyId();
        }

        Long surveyMemberId = surveyId == null
                ? null
                : travelSurveyRepository.findById(surveyId)
                .map(TravelSurvey::getMemberId)
                .orElse(null);
        if (surveyMemberId != null && surveyMemberId > 0) {
            return surveyMemberId;
        }

        Long requestedMemberId = request == null
                ? null
                : request.getMemberId();
        return requestedMemberId != null && requestedMemberId > 0
                ? requestedMemberId
                : null;
    }

    /** 추천 응답 옵션 한 건을 기존 코스 저장 계약으로 변환한다. */
    private CourseSaveRequest toHistoryRequest(
            Long memberId,
            Long resultId,
            CourseRecommendResponse response,
            CourseRecommendRequest request,
            CourseOptionResponse option,
            TransportMode transportMode
    ) {
        String travelCode = response.getTravelCode() != null
                ? response.getTravelCode()
                : request == null ? null : request.getTravelCode();
        boolean routeDetailsResolved = option.getDays() != null
                && option.getDays().stream().anyMatch(day ->
                day != null
                        && Boolean.TRUE.equals(
                        day.getRouteDetailsAttempted()
                )
        );

        return CourseSaveRequest.builder()
                .courseId(option.getCourseId())
                .routeDetailsResolved(routeDetailsResolved)
                .memberId(memberId)
                .resultId(resultId)
                .title(option.getTitle() != null
                        ? option.getTitle()
                        : option.getOptionName())
                .description(option.getDescription())
                .travelCode(travelCode)
                .transportMode(transportMode)
                .courseType("SURVEY")
                .region(option.getRegion())
                .publicCourse(false)
                .places(toHistoryPlaces(option.getDays()))
                .build();
    }

    /**
     * 날짜마다 화면에 표시되는 일반 장소 순서를 저장 형식으로 변환한다.
     *
     * <p>DAY 2 이후는 전날 마지막 숙소에서 출발하지만, 추천 응답의 숙소는
     * 전날 마지막 장소에만 들어 있고 다음 날에는 별도 출발점으로 표시된다.
     * 이 경우 다음 날 첫 일반 장소에 들어 있는 숙소 출발 거리·시간·경로
     * 종류를 0으로 지우지 않고 그대로 저장한다.</p>
     */
    private List<CourseSavePlaceDto> toHistoryPlaces(
            List<CourseDayResponse> days
    ) {
        if (days == null || days.isEmpty()) {
            return List.of();
        }

        List<CourseDayResponse> sortedDays = days.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparing(
                                CourseDayResponse::getVisitDate,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(
                                CourseDayResponse::getDayNo,
                                Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
        List<CourseSavePlaceDto> places = new ArrayList<>();
        CoursePlaceResponse previousDayLastHotel = null;

        for (CourseDayResponse day : sortedDays) {
            LocalDate visitDate = day.getVisitDate();
            if (visitDate == null || day.getPlaces() == null) {
                continue;
            }

            List<CoursePlaceResponse> dayPlaces = day.getPlaces().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(place -> place.getPlaceId() != null)
                    .sorted(Comparator.comparing(
                            CoursePlaceResponse::getVisitOrder,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .toList();
            boolean startsWithHotel = !dayPlaces.isEmpty()
                    && isHotel(dayPlaces.get(0));
            boolean startsFromPreviousDayHotel =
                    previousDayLastHotel != null && !startsWithHotel;

            for (int index = 0; index < dayPlaces.size(); index++) {
                CoursePlaceResponse place = dayPlaces.get(index);
                boolean firstPlace = index == 0
                        && !startsFromPreviousDayHotel;

                places.add(CourseSavePlaceDto.builder()
                        .placeId(place.getPlaceId())
                        .category(place.getCategory())
                        .visitDate(visitDate)
                        .visitOrder(index + 1)
                        .visitTime(place.getVisitTime())
                        .expectedVisitMinutes(nonNegativeInteger(
                                place.getExpectedVisitMinutes()
                        ))
                        .distanceFromPreviousKm(firstPlace
                                ? 0.0
                                : nonNegativeDouble(
                                        place.getDistanceFromPreviousKm()
                                ))
                        .travelTimeFromPreviousMinutes(firstPlace
                                ? 0.0
                                : nonNegativeDouble(
                                        place.getTravelTimeFromPreviousMinutes()
                                ))
                        .transitPathType(firstPlace
                                ? null
                                : place.getTransitPathType())
                        .routeEstimated(!firstPlace
                                && Boolean.TRUE.equals(
                                        place.getRouteEstimated()
                        ))
                        .build());
            }

            previousDayLastHotel = dayPlaces.stream()
                    .filter(this::isHotel)
                    .max(Comparator.comparing(
                            CoursePlaceResponse::getVisitOrder,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .orElse(null);
        }

        return List.copyOf(places);
    }

    private boolean isHotel(CoursePlaceResponse place) {
        if (place == null || place.getCategory() == null) {
            return false;
        }
        String category = place.getCategory().trim()
                .toUpperCase(java.util.Locale.ROOT);
        return category.equals("HOTEL")
                || category.equals("숙소")
                || category.equals("호텔")
                || category.equals("ACCOMMODATION")
                || category.equals("LODGING");
    }

    private int nonNegativeInteger(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private double nonNegativeDouble(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, value);
    }
}
