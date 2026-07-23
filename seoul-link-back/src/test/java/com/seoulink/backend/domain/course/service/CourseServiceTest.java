package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
import com.seoulink.backend.domain.course.entity.CourseDetail;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import com.seoulink.backend.domain.survey.repository.SurveyResultRepository;
import com.seoulink.backend.domain.survey.repository.TravelSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 저장 엔티티가 상세 응답과 목록 카드 응답으로 올바르게 변환되는지 검증한다. */
class CourseServiceTest {

    private TravelCourseRepository travelCourseRepository;
    private CourseDetailRepository courseDetailRepository;
    private PlaceRepository placeRepository;
    private SurveyResultRepository surveyResultRepository;
    private TravelSurveyRepository travelSurveyRepository;
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        // 조회 순서와 응답 변환만 확인할 수 있도록 Repository를 mock으로 구성한다.
        travelCourseRepository = mock(TravelCourseRepository.class);
        courseDetailRepository = mock(CourseDetailRepository.class);
        placeRepository = mock(PlaceRepository.class);
        surveyResultRepository = mock(SurveyResultRepository.class);
        travelSurveyRepository = mock(TravelSurveyRepository.class);
        when(placeRepository.findAllById(any())).thenReturn(List.of());
        courseService = new CourseService(
                travelCourseRepository,
                courseDetailRepository,
                placeRepository,
                surveyResultRepository,
                travelSurveyRepository
        );
    }

    @Test
    @DisplayName("저장된 코스와 날짜별 장소 순서를 조회한다")
    void getCourse() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 15, 15, 0);
        TravelCourse course = TravelCourse.builder()
                .courseId(10L)
                .memberId(1L)
                .resultId(5L)
                .title("서울 궁궐 코스")
                .description("서울 궁궐을 둘러보는 코스")
                .travelCode("ATLSR")
                .courseType("SURVEY")
                .region("서울 종로구")
                .publicStatus("Y")
                .viewCount(12L)
                .totalDistanceKm(0.27)
                .totalTravelTimeMinutes(3.56)
                .totalVisitTimeMinutes(270)
                .totalCourseTimeMinutes(273.56)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        List<CourseDetail> details = List.of(
                detail(100L, 10L, 1L, 1, 1, visitDate, 90, 0.0, 0.0),
                detail(
                        101L,
                        10L,
                        2L,
                        1,
                        2,
                        visitDate,
                        90,
                        0.27,
                        3.56,
                        TransitPathType.SUBWAY
                ),
                detail(102L, 10L, 3L, 2, 1, visitDate.plusDays(1), 90, 0.0, 0.0)
        );

        when(travelCourseRepository.findById(10L))
                .thenReturn(Optional.of(course));
        when(courseDetailRepository
                .findByCourseIdOrderByDayNoAscPlaceOrderAsc(10L))
                .thenReturn(details);
        when(placeRepository.findAllById(any())).thenReturn(List.of(
                place(1L, "경복궁", "TOUR", "서울 종로구 사직로 161", "palace.jpg", true),
                place(2L, "북촌한옥마을", "TOUR", "서울 종로구 계동길 37", null, false),
                place(3L, "인사동 카페", "CAFE", "서울 종로구 인사동길", null, false)
        ));
        mockTransportMode(5L, 50L, "PUBLIC");

        CourseDetailResponse response = courseService.getCourse(10L);

        assertEquals(10L, response.getCourseId());
        assertEquals("서울 궁궐 코스", response.getTitle());
        assertEquals(TransportMode.PUBLIC_TRANSIT, response.getTransportMode());
        assertEquals("palace.jpg", response.getCoverImageUrl());
        assertEquals(true, response.getPublicCourse());
        assertEquals(3, response.getPlaceCount());
        assertEquals(2, response.getDayCount());
        assertEquals(List.of(1, 2), response.getDays().stream()
                .map(day -> day.getDayNo())
                .toList());
        assertEquals(
                List.of(visitDate, visitDate.plusDays(1)),
                response.getDays().stream()
                        .map(day -> day.getVisitDate())
                        .toList()
        );
        assertEquals(List.of(1L, 2L), response.getDays().get(0).getPlaces().stream()
                .map(place -> place.getPlaceId())
                .toList());
        assertEquals("경복궁", response.getDays().get(0).getPlaces().get(0).getPlaceName());
        assertEquals(
                "서울 종로구 사직로 161",
                response.getDays().get(0).getPlaces().get(0).getAddress()
        );
        assertEquals(List.of(1, 2), response.getDays().get(0).getPlaces().stream()
                .map(place -> place.getVisitOrder())
                .toList());
        assertEquals(
                90,
                response.getDays().get(0).getPlaces().get(0).getExpectedVisitMinutes()
        );
        assertEquals(
                TransitPathType.SUBWAY,
                response.getDays().get(0).getPlaces().get(1).getTransitPathType()
        );
        assertEquals(
                0.27,
                response.getDays().get(0).getDailyDistanceKm(),
                0.000001
        );
        assertEquals(
                3.56,
                response.getDays().get(0).getDailyTravelTimeMinutes(),
                0.000001
        );
        assertEquals(180, response.getDays().get(0).getDailyVisitTimeMinutes());
        assertEquals(
                183.56,
                response.getDays().get(0).getDailyCourseTimeMinutes(),
                0.000001
        );
        assertEquals(0.0, response.getDays().get(1).getDailyDistanceKm(), 0.000001);
        assertEquals(90, response.getDays().get(1).getDailyVisitTimeMinutes());
        assertEquals(
                90.0,
                response.getDays().get(1).getDailyCourseTimeMinutes(),
                0.000001
        );
    }

    @Test
    @DisplayName("존재하지 않는 코스를 조회하면 예외를 반환한다")
    void rejectUnknownCourse() {
        when(travelCourseRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> courseService.getCourse(999L)
        );
    }

    @Test
    @DisplayName("회원은 자신이 저장한 비공개 코스 상세만 조회한다")
    void getMemberCourse() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        TravelCourse privateCourse = TravelCourse.builder()
                .courseId(11L)
                .memberId(1L)
                .title("내 비공개 코스")
                .publicStatus("N")
                .build();
        List<CourseDetail> details = List.of(detail(
                110L,
                11L,
                1L,
                1,
                1,
                visitDate,
                90,
                0.0,
                0.0
        ));

        when(travelCourseRepository.findByCourseIdAndMemberId(11L, 1L))
                .thenReturn(Optional.of(privateCourse));
        when(courseDetailRepository
                .findByCourseIdOrderByDayNoAscPlaceOrderAsc(11L))
                .thenReturn(details);

        CourseDetailResponse response = courseService.getMemberCourse(11L, 1L);

        assertEquals(11L, response.getCourseId());
        assertEquals("내 비공개 코스", response.getTitle());
        assertEquals(1, response.getPlaceCount());
    }

    @Test
    @DisplayName("다른 회원의 비공개 코스 상세는 존재하지 않는 코스처럼 처리한다")
    void rejectOtherMembersCourse() {
        when(travelCourseRepository.findByCourseIdAndMemberId(11L, 2L))
                .thenReturn(Optional.empty());

        assertThrows(
                NoSuchElementException.class,
                () -> courseService.getMemberCourse(11L, 2L)
        );
    }

    @Test
    @DisplayName("회원의 SURVEY 추천 코스만 최신순으로 조회한다")
    void getRecommendedCourses() {
        TravelCourse recommended = TravelCourse.builder()
                .courseId(20L)
                .memberId(1L)
                .resultId(20L)
                .title("서울 추천 코스")
                .description("설문 기반 추천 코스")
                .courseType("SURVEY")
                .region("서울 종로구")
                .totalDistanceKm(2.5)
                .totalTravelTimeMinutes(30.0)
                .totalVisitTimeMinutes(180)
                .totalCourseTimeMinutes(210.0)
                .build();
        List<CourseDetail> details = List.of(
                detail(200L, 20L, 1L, 1, 1,
                        LocalDate.of(2026, 7, 20), 90, 0.0, 0.0),
                detail(201L, 20L, 2L, 2, 1,
                        LocalDate.of(2026, 7, 21), 90, 0.0, 0.0)
        );

        when(travelCourseRepository
                .findByMemberIdAndCourseTypeOrderByCreatedAtDesc(1L, "SURVEY"))
                .thenReturn(List.of(recommended));
        when(courseDetailRepository
                .findByCourseIdOrderByDayNoAscPlaceOrderAsc(20L))
                .thenReturn(details);
        when(placeRepository.findAllById(any())).thenReturn(List.of(
                place(1L, "경복궁", "TOUR", "서울 종로구", "palace.jpg", true),
                place(2L, "서울숲", "TOUR", "서울 성동구", null, false)
        ));
        mockTransportMode(20L, 200L, "PUBLIC_TRANSIT");

        List<CourseRecommendationResponse> response =
                courseService.getRecommendedCourses(1L);

        assertEquals(1, response.size());
        assertEquals(20L, response.get(0).getCourseId());
        assertEquals(
                TransportMode.PUBLIC_TRANSIT,
                response.get(0).getTransportMode()
        );
        assertEquals(2, response.get(0).getPlaceCount());
        assertEquals(2, response.get(0).getDayCount());
        assertEquals(List.of("서울 종로구"), response.get(0).getRegions());
        assertEquals("palace.jpg", response.get(0).getCoverImageUrl());
        assertEquals(List.of("역사·문화"), response.get(0).getTags());
    }

    @Test
    @DisplayName("회원의 모든 유형 코스를 최신순으로 조회한다")
    void getMemberCourses() {
        TravelCourse custom = TravelCourse.builder()
                .courseId(30L)
                .memberId(1L)
                .title("내 서울 코스")
                .courseType("CUSTOM")
                .build();

        when(travelCourseRepository.findByMemberIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(custom));
        when(courseDetailRepository
                .findByCourseIdOrderByDayNoAscPlaceOrderAsc(30L))
                .thenReturn(List.of(detail(
                        300L,
                        30L,
                        1L,
                        1,
                        1,
                        LocalDate.of(2026, 7, 20),
                        90,
                        0.0,
                        0.0
                )));

        List<CourseRecommendationResponse> response =
                courseService.getMemberCourses(1L);

        assertEquals(1, response.size());
        assertEquals(30L, response.get(0).getCourseId());
        assertEquals("내 서울 코스", response.get(0).getTitle());
        // CUSTOM 코스는 설문 RESULT_ID가 없으므로 기존 스키마에서 이동수단을 복원할 수 없다.
        assertNull(response.get(0).getTransportMode());
        assertEquals(1, response.get(0).getPlaceCount());
        assertEquals("CUSTOM", response.get(0).getCourseType());
        assertEquals(LocalDate.of(2026, 7, 20), response.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 7, 20), response.get(0).getEndDate());
    }

    @Test
    @DisplayName("설문 DB 이동수단 값을 코스 API enum으로 변환한다")
    void convertSurveyTransportTypes() {
        assertEquals(
                TransportMode.PUBLIC_TRANSIT,
                TransportMode.fromSurveyTransportType("PUBLIC")
        );
        assertEquals(
                TransportMode.WALKING,
                TransportMode.fromSurveyTransportType("walking")
        );
        assertEquals(
                TransportMode.DRIVING,
                TransportMode.fromSurveyTransportType("CAR")
        );
        assertNull(TransportMode.fromSurveyTransportType("UNKNOWN"));
    }

    /** 날짜와 순서가 다른 상세 장소 엔티티를 만드는 테스트 헬퍼이다. */
    private CourseDetail detail(
            Long detailId,
            Long courseId,
            Long placeId,
            Integer dayNo,
            Integer placeOrder,
            LocalDate visitDate,
            Integer stayMinutes,
            Double distanceKm,
            Double travelMinutes
    ) {
        return detail(
                detailId,
                courseId,
                placeId,
                dayNo,
                placeOrder,
                visitDate,
                stayMinutes,
                distanceKm,
                travelMinutes,
                null
        );
    }

    private CourseDetail detail(
            Long detailId,
            Long courseId,
            Long placeId,
            Integer dayNo,
            Integer placeOrder,
            LocalDate visitDate,
            Integer stayMinutes,
            Double distanceKm,
            Double travelMinutes,
            TransitPathType transitPathType
    ) {
        return CourseDetail.builder()
                .detailId(detailId)
                .courseId(courseId)
                .placeId(placeId)
                .dayNo(dayNo)
                .placeOrder(placeOrder)
                .visitDate(visitDate)
                .stayMinutes(stayMinutes)
                .distanceFromPreviousKm(distanceKm)
                .travelTimeFromPreviousMinutes(travelMinutes)
                .transitPathType(transitPathType)
                .build();
    }

    /** TRAVEL_COURSES.RESULT_ID에서 기존 TRAVEL_SURVEY.TRANSPORT_TYPE을 찾도록 mock한다. */
    private void mockTransportMode(
            Long resultId,
            Long surveyId,
            String transportType
    ) {
        SurveyResult surveyResult = mock(SurveyResult.class);
        TravelSurvey travelSurvey = mock(TravelSurvey.class);
        when(surveyResult.getSurveyId()).thenReturn(surveyId);
        when(travelSurvey.getTransportType()).thenReturn(transportType);
        when(surveyResultRepository.findById(resultId))
                .thenReturn(Optional.of(surveyResult));
        when(travelSurveyRepository.findById(surveyId))
                .thenReturn(Optional.of(travelSurvey));
    }

    private Place place(
            Long placeId,
            String name,
            String category,
            String address,
            String imageUrl,
            boolean palaceTheme
    ) {
        Place place = new Place();
        place.setPlaceId(placeId);
        place.setName(name);
        place.setCategory(category);
        place.setAddress(address);
        place.setImageUrl(imageUrl);
        place.setLatitude(37.5665);
        place.setLongitude(126.9780);
        place.setThemePalaceCultureYn(palaceTheme ? "Y" : "N");
        return place;
    }
}
