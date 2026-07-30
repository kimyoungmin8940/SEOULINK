package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.CourseSavePlaceDto;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptionResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
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

        List<CourseSaveRequest> historyRequests = response.getCourseOptions()
                == null
                ? List.of()
                : response.getCourseOptions().stream()
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

        courseSaveService.saveRecommendationHistory(historyRequests);
    }

    /** 브라우저 세션에 남은 기존 추천 응답을 현재 회원 이력으로 복구한다. */
    @Transactional
    public void record(CourseRecommendResponse response) {
        record(null, response);
    }

    /** 설문 결과에서 원래 설문을 찾고, 그 설문을 수행한 회원 ID를 복원한다. */
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
        if (surveyId == null) {
            return null;
        }

        return travelSurveyRepository.findById(surveyId)
                .map(TravelSurvey::getMemberId)
                .orElse(null);
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

        return CourseSaveRequest.builder()
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

    /** 날짜마다 출발 장소를 1번으로 두고 저장 검증에 맞는 연속 순서를 만든다. */
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

            for (int index = 0; index < dayPlaces.size(); index++) {
                CoursePlaceResponse place = dayPlaces.get(index);
                boolean firstPlace = index == 0;

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
        }

        return List.copyOf(places);
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
