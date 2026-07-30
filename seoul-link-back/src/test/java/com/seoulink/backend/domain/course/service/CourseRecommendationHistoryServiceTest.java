package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.CourseSavePlaceDto;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptionResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import com.seoulink.backend.domain.survey.repository.SurveyResultRepository;
import com.seoulink.backend.domain.survey.repository.TravelSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseRecommendationHistoryServiceTest {

    private SurveyResultRepository surveyResultRepository;
    private TravelSurveyRepository travelSurveyRepository;
    private CourseSaveService courseSaveService;
    private CourseRecommendationHistoryService historyService;

    @BeforeEach
    void setUp() {
        surveyResultRepository = mock(SurveyResultRepository.class);
        travelSurveyRepository = mock(TravelSurveyRepository.class);
        courseSaveService = mock(CourseSaveService.class);
        historyService = new CourseRecommendationHistoryService(
                surveyResultRepository,
                travelSurveyRepository,
                courseSaveService
        );
    }

    @Test
    @DisplayName("로그인 회원에게 반환한 추천 옵션 전체를 저장 전 이력으로 기록한다")
    void recordAllGeneratedOptionsForMember() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        SurveyResult result = SurveyResult.create(501L, "ATLSR");
        TravelSurvey survey = TravelSurvey.createGuestSurvey(
                "guest-history-test",
                "서울",
                visitDate,
                visitDate,
                "FRIEND",
                "WALKING",
                LocalDateTime.now().plusDays(1)
        );
        survey.claimByMember(1L);

        when(surveyResultRepository.findById(101L))
                .thenReturn(Optional.of(result));
        when(travelSurveyRepository.findById(501L))
                .thenReturn(Optional.of(survey));

        CourseDayResponse day = CourseDayResponse.builder()
                .dayNo(1)
                .visitDate(visitDate)
                .routeDetailsAttempted(true)
                .places(List.of(
                        CoursePlaceResponse.builder()
                                .placeId(10L)
                                .category("TOUR")
                                .visitOrder(1)
                                .visitTime("11:00")
                                .expectedVisitMinutes(90)
                                .distanceFromPreviousKm(0.0)
                                .travelTimeFromPreviousMinutes(0.0)
                                .build(),
                        CoursePlaceResponse.builder()
                                .placeId(20L)
                                .category("CAFE")
                                .visitOrder(2)
                                .visitTime("13:00")
                                .expectedVisitMinutes(60)
                                .distanceFromPreviousKm(0.5)
                                .travelTimeFromPreviousMinutes(7.0)
                                .build()
                ))
                .build();
        CourseRecommendResponse response = CourseRecommendResponse.builder()
                .resultId(101L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.WALKING)
                .courseOptions(List.of(
                        option(1, "취향 집중 코스", day),
                        option(2, "이동 최소 코스", day),
                        option(3, "균형 추천 코스", day)
                ))
                .build();
        when(courseSaveService.saveRecommendationHistory(anyList()))
                .thenReturn(List.of(
                        savedCourse(71L, "취향 집중 코스"),
                        savedCourse(72L, "이동 최소 코스"),
                        savedCourse(73L, "균형 추천 코스")
                ));

        historyService.record(
                CourseRecommendRequest.builder()
                        .surveyId(501L)
                        .resultId(101L)
                        .travelCode("ATLSR")
                        .transportMode(TransportMode.WALKING)
                        .build(),
                response
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CourseSaveRequest>> requestsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(courseSaveService).saveRecommendationHistory(
                requestsCaptor.capture()
        );
        List<CourseSaveRequest> requests = requestsCaptor.getValue();

        assertEquals(3, requests.size());
        assertEquals(1L, requests.get(0).getMemberId());
        assertEquals(101L, requests.get(0).getResultId());
        assertEquals("SURVEY", requests.get(0).getCourseType());
        assertEquals("취향 집중 코스", requests.get(0).getTitle());
        assertEquals(true, requests.get(0).getRouteDetailsResolved());
        assertEquals(2, requests.get(0).getPlaces().size());
        assertEquals(1, requests.get(0).getPlaces().get(0).getVisitOrder());
        assertEquals(2, requests.get(0).getPlaces().get(1).getVisitOrder());
        assertEquals(71L, response.getCourseOptions().get(0).getCourseId());
        assertEquals(72L, response.getCourseOptions().get(1).getCourseId());
        assertEquals(73L, response.getCourseOptions().get(2).getCourseId());
    }

    @Test
    @DisplayName("DAY 2 첫 장소의 전날 숙소 출발 실제 경로값을 이력에 유지한다")
    void preserveHotelOriginRouteForFirstPlaceAfterDayOne() {
        LocalDate firstDate = LocalDate.of(2026, 7, 20);
        LocalDate secondDate = LocalDate.of(2026, 7, 21);
        TravelSurvey survey = TravelSurvey.createGuestSurvey(
                "hotel-origin-history-test",
                "서울",
                firstDate,
                secondDate,
                "FRIEND",
                "PUBLIC_TRANSIT",
                LocalDateTime.now().plusDays(1)
        );
        survey.claimByMember(1L);
        when(surveyResultRepository.findById(101L))
                .thenReturn(Optional.of(SurveyResult.create(501L, "ATLSR")));
        when(travelSurveyRepository.findById(501L))
                .thenReturn(Optional.of(survey));

        CourseDayResponse firstDay = CourseDayResponse.builder()
                .dayNo(1)
                .visitDate(firstDate)
                .places(List.of(
                        CoursePlaceResponse.builder()
                                .placeId(10L)
                                .category("TOUR")
                                .visitOrder(1)
                                .expectedVisitMinutes(90)
                                .distanceFromPreviousKm(0.0)
                                .travelTimeFromPreviousMinutes(0.0)
                                .build(),
                        CoursePlaceResponse.builder()
                                .placeId(100L)
                                .category("HOTEL")
                                .visitOrder(2)
                                .expectedVisitMinutes(0)
                                .distanceFromPreviousKm(2.0)
                                .travelTimeFromPreviousMinutes(20.0)
                                .build()
                ))
                .build();
        CourseDayResponse secondDay = CourseDayResponse.builder()
                .dayNo(2)
                .visitDate(secondDate)
                .routeDetailsAttempted(true)
                .places(List.of(CoursePlaceResponse.builder()
                        .placeId(20L)
                        .category("RESTAURANT")
                        .visitOrder(1)
                        .visitTime("13:34")
                        .expectedVisitMinutes(60)
                        .distanceFromPreviousKm(7.6)
                        .travelTimeFromPreviousMinutes(34.0)
                        .transitPathType(TransitPathType.BUS)
                        .routeEstimated(true)
                        .build()))
                .build();
        CourseRecommendResponse response = CourseRecommendResponse.builder()
                .resultId(101L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .courseOptions(List.of(CourseOptionResponse.builder()
                        .optionNo(1)
                        .title("숙소 출발 코스")
                        .days(List.of(firstDay, secondDay))
                        .build()))
                .build();
        when(courseSaveService.saveRecommendationHistory(anyList()))
                .thenReturn(List.of(savedCourse(71L, "숙소 출발 코스")));

        historyService.record(
                CourseRecommendRequest.builder()
                        .resultId(101L)
                        .transportMode(TransportMode.PUBLIC_TRANSIT)
                        .build(),
                response
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CourseSaveRequest>> requestsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(courseSaveService).saveRecommendationHistory(
                requestsCaptor.capture()
        );
        List<CourseSavePlaceDto> savedPlaces =
                requestsCaptor.getValue().get(0).getPlaces();
        var secondDayFirstPlace = savedPlaces.get(2);

        assertEquals(secondDate, secondDayFirstPlace.getVisitDate());
        assertEquals(1, secondDayFirstPlace.getVisitOrder());
        assertEquals(7.6,
                secondDayFirstPlace.getDistanceFromPreviousKm(), 0.000001);
        assertEquals(34.0,
                secondDayFirstPlace.getTravelTimeFromPreviousMinutes(),
                0.000001);
        assertEquals(TransitPathType.BUS,
                secondDayFirstPlace.getTransitPathType());
        assertEquals(true, secondDayFirstPlace.getRouteEstimated());
    }

    @Test
    @DisplayName("비회원 추천은 회원 추천 이력에 저장하지 않는다")
    void skipGuestRecommendationHistory() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        TravelSurvey guestSurvey = TravelSurvey.createGuestSurvey(
                "guest-only-history-test",
                "서울",
                visitDate,
                visitDate,
                "SOLO",
                "WALKING",
                LocalDateTime.now().plusDays(1)
        );

        when(surveyResultRepository.findById(101L))
                .thenReturn(Optional.of(SurveyResult.create(501L, "ATLSR")));
        when(travelSurveyRepository.findById(501L))
                .thenReturn(Optional.of(guestSurvey));

        historyService.record(
                CourseRecommendRequest.builder()
                        .surveyId(501L)
                        .resultId(101L)
                        .transportMode(TransportMode.WALKING)
                        .build(),
                CourseRecommendResponse.builder()
                        .resultId(101L)
                        .transportMode(TransportMode.WALKING)
                        .courseOptions(List.of())
                        .build()
        );

        verify(courseSaveService, never())
                .saveRecommendationHistory(anyList());
    }

    @Test
    @DisplayName("게스트 설문도 추천 요청의 로그인 회원에게 결과 전체를 기록한다")
    void recordGuestSurveyRecommendationForRequestedMember() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        TravelSurvey guestSurvey = TravelSurvey.createGuestSurvey(
                "guest-member-history-test",
                "서울",
                visitDate,
                visitDate,
                "SOLO",
                "WALKING",
                LocalDateTime.now().plusDays(1)
        );
        when(surveyResultRepository.findById(101L))
                .thenReturn(Optional.of(SurveyResult.create(501L, "ATLSR")));
        when(travelSurveyRepository.findById(501L))
                .thenReturn(Optional.of(guestSurvey));

        CourseDayResponse day = CourseDayResponse.builder()
                .dayNo(1)
                .visitDate(visitDate)
                .places(List.of(CoursePlaceResponse.builder()
                        .placeId(10L)
                        .category("TOUR")
                        .visitOrder(1)
                        .expectedVisitMinutes(90)
                        .distanceFromPreviousKm(0.0)
                        .travelTimeFromPreviousMinutes(0.0)
                        .build()))
                .build();
        CourseRecommendResponse response = CourseRecommendResponse.builder()
                .resultId(101L)
                .travelCode("ATLSR")
                .transportMode(TransportMode.WALKING)
                .courseOptions(List.of(
                        option(1, "취향 집중 코스", day),
                        option(2, "이동 최소 코스", day),
                        option(3, "균형 추천 코스", day)
                ))
                .build();

        historyService.record(
                CourseRecommendRequest.builder()
                        .memberId(7L)
                        .resultId(101L)
                        .transportMode(TransportMode.WALKING)
                        .build(),
                response
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CourseSaveRequest>> requestsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(courseSaveService).saveRecommendationHistory(
                requestsCaptor.capture()
        );
        assertEquals(3, requestsCaptor.getValue().size());
        assertEquals(7L, requestsCaptor.getValue().get(0).getMemberId());
    }

    private CourseOptionResponse option(
            int optionNo,
            String title,
            CourseDayResponse day
    ) {
        return CourseOptionResponse.builder()
                .optionNo(optionNo)
                .optionName(title)
                .title(title)
                .description(title + " 설명")
                .region("서울 종로구")
                .days(List.of(day))
                .build();
    }

    private CourseSaveResponse savedCourse(Long courseId, String title) {
        return CourseSaveResponse.builder()
                .courseId(courseId)
                .title(title)
                .build();
    }
}
