package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseBatchSaveRequest;
import com.seoulink.backend.domain.course.dto.request.CourseSavePlaceDto;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseBatchSaveResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.entity.CourseDetail;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 코스 기본 행과 상세 장소 행의 저장값, 검증 규칙, 트랜잭션 경계를 확인한다. */
class CourseSaveServiceTest {

    private TravelCourseRepository travelCourseRepository;
    private CourseDetailRepository courseDetailRepository;
    private CourseSaveService courseSaveService;

    @BeforeEach
    void setUp() {
        // Repository를 mock으로 두어 DB 없이 저장 호출과 전달 엔티티를 검증한다.
        travelCourseRepository = mock(TravelCourseRepository.class);
        courseDetailRepository = mock(CourseDetailRepository.class);
        courseSaveService = new CourseSaveService(
                travelCourseRepository,
                courseDetailRepository
        );
    }

    @Test
    @DisplayName("최적화 코스와 날짜별 장소 순서를 함께 저장한다")
    void saveOptimizedCourse() {
        LocalDate firstDay = LocalDate.of(2026, 7, 20);
        LocalDate secondDay = LocalDate.of(2026, 7, 21);
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .resultId(5L)
                .title("서울 궁궐과 카페 코스")
                .description("서울의 궁궐과 카페를 함께 둘러보는 코스")
                .travelCode("atlsr")
                .courseType("survey")
                .region("서울 종로구")
                .places(List.of(
                        place(1L, firstDay, 1, 90, 0.0, 0.0),
                        place(2L, firstDay, 2, 60, 0.267191, 3.5625),
                        place(3L, secondDay, 1, 90, 0.0, 0.0)
                ))
                .build();
        request.getPlaces().get(0).setVisitTime("10:00");

        when(travelCourseRepository.save(any(TravelCourse.class)))
                .thenAnswer(invocation -> {
                    TravelCourse course = invocation.getArgument(0);
                    return TravelCourse.builder()
                            .courseId(10L)
                            .title(course.getTitle())
                            .build();
                });

        CourseSaveResponse response = courseSaveService.saveOptimizedCourse(request);

        ArgumentCaptor<TravelCourse> courseCaptor =
                ArgumentCaptor.forClass(TravelCourse.class);
        verify(travelCourseRepository).save(courseCaptor.capture());
        TravelCourse course = courseCaptor.getValue();

        assertEquals(1L, course.getMemberId());
        assertEquals("ATLSR", course.getTravelCode());
        assertEquals("SURVEY", course.getCourseType());
        assertEquals("Y", course.getSavedStatus());
        assertEquals(0.267, course.getTotalDistanceKm(), 0.000001);
        assertEquals(3.56, course.getTotalTravelTimeMinutes(), 0.000001);
        assertEquals(240, course.getTotalVisitTimeMinutes());
        assertEquals(243.56, course.getTotalCourseTimeMinutes(), 0.000001);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<CourseDetail>> detailCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(courseDetailRepository).saveAll(detailCaptor.capture());
        List<CourseDetail> details = StreamSupport
                .stream(detailCaptor.getValue().spliterator(), false)
                .toList();

        assertEquals(List.of(1, 1, 2), details.stream()
                .map(CourseDetail::getDayNo)
                .toList());
        assertEquals(List.of(1, 2, 1), details.stream()
                .map(CourseDetail::getPlaceOrder)
                .toList());
        assertEquals(List.of(firstDay, firstDay, secondDay), details.stream()
                .map(CourseDetail::getVisitDate)
                .toList());
        assertEquals("10:00", details.get(0).getVisitTime());

        assertEquals(10L, response.getCourseId());
        assertEquals(TransportMode.WALKING, response.getTransportMode());
        assertEquals(3, response.getPlaceCount());
        assertEquals(2, response.getDayCount());
        assertEquals(0.267, response.getTotalDistanceKm(), 0.000001);
        assertEquals(3.56, response.getTotalTravelTimeMinutes(), 0.000001);
        assertEquals(240, response.getTotalVisitTimeMinutes());
        assertEquals(243.56, response.getTotalCourseTimeMinutes(), 0.000001);
    }

    @Test
    @DisplayName("같은 설문 추천 코스를 다시 저장하면 기존 코스를 반환한다")
    void reuseExistingSurveyCourseInsteadOfSavingDuplicate() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .resultId(101L)
                .title("이미 저장한 취향 집중 코스")
                .courseType("SURVEY")
                .places(List.of(
                        place(1L, visitDate, 1, 90, 0.0, 0.0),
                        place(2L, visitDate, 2, 60, 0.3, 4.0)
                ))
                .build();
        TravelCourse existingCourse = TravelCourse.builder()
                .courseId(77L)
                .memberId(1L)
                .resultId(101L)
                .title("이미 저장한 취향 집중 코스")
                .courseType("SURVEY")
                .savedStatus("N")
                .totalDistanceKm(0.3)
                .totalTravelTimeMinutes(4.0)
                .totalVisitTimeMinutes(150)
                .totalCourseTimeMinutes(154.0)
                .build();
        List<CourseDetail> existingDetails = List.of(
                CourseDetail.builder()
                        .courseId(77L)
                        .placeId(2L)
                        .dayNo(1)
                        .placeOrder(2)
                        .visitDate(visitDate)
                        .build(),
                CourseDetail.builder()
                        .courseId(77L)
                        .placeId(1L)
                        .dayNo(1)
                        .placeOrder(1)
                        .visitDate(visitDate)
                        .build()
        );

        when(travelCourseRepository
                .findByMemberIdAndResultIdAndCourseTypeOrderByCreatedAtDesc(
                        1L,
                        101L,
                        "SURVEY"
                ))
                .thenReturn(List.of(existingCourse));
        when(courseDetailRepository
                .findByCourseIdOrderByDayNoAscPlaceOrderAsc(77L))
                .thenReturn(existingDetails);

        CourseSaveResponse response =
                courseSaveService.saveOptimizedCourse(request);

        assertEquals(77L, response.getCourseId());
        assertEquals(2, response.getPlaceCount());
        assertEquals(1, response.getDayCount());
        assertTrue(existingCourse.isSaved());
        verify(travelCourseRepository, never()).save(any());
        verify(courseDetailRepository).deleteAllInBatch(existingDetails);
        verify(courseDetailRepository).saveAll(any());
    }

    @Test
    @DisplayName("추천 생성 직후 코스는 내 코스에 넣지 않고 추천 이력으로 저장한다")
    void saveGeneratedRecommendationAsUnsavedHistory() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .resultId(101L)
                .title("추천받은 취향 집중 코스")
                .courseType("SURVEY")
                .places(List.of(
                        place(1L, visitDate, 1, 90, 0.0, 0.0)
                ))
                .build();

        when(travelCourseRepository.save(any(TravelCourse.class)))
                .thenAnswer(invocation -> {
                    TravelCourse course = invocation.getArgument(0);
                    return TravelCourse.builder()
                            .courseId(88L)
                            .title(course.getTitle())
                            .savedStatus(course.getSavedStatus())
                            .build();
                });

        courseSaveService.saveRecommendationHistory(List.of(request));

        ArgumentCaptor<TravelCourse> courseCaptor =
                ArgumentCaptor.forClass(TravelCourse.class);
        verify(travelCourseRepository).save(courseCaptor.capture());
        assertEquals("N", courseCaptor.getValue().getSavedStatus());
        verify(courseDetailRepository).saveAll(any());
    }

    @Test
    @DisplayName("추천 화면의 실제 경로값을 courseId가 같은 추천 이력 상세에 반영한다")
    void refreshRecommendationHistoryWithResolvedRouteDetails() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSavePlaceDto firstPlace = place(
                1L,
                visitDate,
                1,
                90,
                0.0,
                0.0
        );
        CourseSavePlaceDto secondPlace = place(
                2L,
                visitDate,
                2,
                60,
                2.4,
                18.0
        );
        secondPlace.setTransitPathType(TransitPathType.SUBWAY);
        secondPlace.setRouteEstimated(false);

        CourseSaveRequest request = CourseSaveRequest.builder()
                .courseId(88L)
                .memberId(1L)
                .resultId(101L)
                .title("추천받은 취향 집중 코스")
                .courseType("SURVEY")
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .places(List.of(firstPlace, secondPlace))
                .build();
        TravelCourse historyCourse = TravelCourse.builder()
                .courseId(88L)
                .memberId(1L)
                .resultId(101L)
                .title("추천받은 취향 집중 코스")
                .courseType("SURVEY")
                .savedStatus("N")
                .totalDistanceKm(1.0)
                .totalTravelTimeMinutes(10.0)
                .totalVisitTimeMinutes(150)
                .totalCourseTimeMinutes(160.0)
                .build();
        List<CourseDetail> estimatedDetails = List.of(
                CourseDetail.builder()
                        .detailId(801L)
                        .courseId(88L)
                        .placeId(1L)
                        .dayNo(1)
                        .placeOrder(1)
                        .visitDate(visitDate)
                        .routeEstimated(false)
                        .build(),
                CourseDetail.builder()
                        .detailId(802L)
                        .courseId(88L)
                        .placeId(2L)
                        .dayNo(1)
                        .placeOrder(2)
                        .visitDate(visitDate)
                        .travelTimeFromPreviousMinutes(10.0)
                        .routeEstimated(true)
                        .build()
        );

        when(travelCourseRepository.findByCourseIdAndMemberId(88L, 1L))
                .thenReturn(Optional.of(historyCourse));
        when(courseDetailRepository
                .findByCourseIdOrderByDayNoAscPlaceOrderAsc(88L))
                .thenReturn(estimatedDetails);

        CourseSaveResponse response = courseSaveService
                .saveRecommendationHistory(List.of(request))
                .get(0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<CourseDetail>> detailCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(courseDetailRepository).deleteAllInBatch(estimatedDetails);
        verify(courseDetailRepository).saveAll(detailCaptor.capture());
        List<CourseDetail> refreshedDetails = StreamSupport
                .stream(detailCaptor.getValue().spliterator(), false)
                .toList();

        assertEquals(88L, response.getCourseId());
        assertEquals("N", historyCourse.getSavedStatus());
        assertEquals(2.4, historyCourse.getTotalDistanceKm(), 0.000001);
        assertEquals(18.0, historyCourse.getTotalTravelTimeMinutes(), 0.000001);
        assertEquals(18.0,
                refreshedDetails.get(1).getTravelTimeFromPreviousMinutes(),
                0.000001);
        assertEquals(TransitPathType.SUBWAY,
                refreshedDetails.get(1).getTransitPathType());
        assertEquals(false, refreshedDetails.get(1).getRouteEstimated());
    }

    @Test
    @DisplayName("대중교통 구간 종류를 COURSE_DETAILS에 함께 저장한다")
    void saveTransitPathType() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSavePlaceDto firstPlace = place(
                1L,
                visitDate,
                1,
                90,
                0.0,
                0.0
        );
        CourseSavePlaceDto secondPlace = place(
                2L,
                visitDate,
                2,
                60,
                3.2,
                24.0
        );
        secondPlace.setTransitPathType(TransitPathType.SUBWAY);
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .memberId(1L)
                .title("대중교통 서울 코스")
                .places(List.of(firstPlace, secondPlace))
                .build();

        when(travelCourseRepository.save(any(TravelCourse.class)))
                .thenReturn(TravelCourse.builder()
                        .courseId(30L)
                        .title("대중교통 서울 코스")
                        .build());

        courseSaveService.saveOptimizedCourse(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<CourseDetail>> detailCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(courseDetailRepository).saveAll(detailCaptor.capture());
        List<CourseDetail> details = StreamSupport
                .stream(detailCaptor.getValue().spliterator(), false)
                .toList();

        assertNull(details.get(0).getTransitPathType());
        assertEquals(TransitPathType.SUBWAY, details.get(1).getTransitPathType());
    }

    @Test
    @DisplayName("DAY 2 첫 장소에는 전날 숙소 출발 경로를 저장할 수 있다")
    void savePreviousHotelRouteOnFirstPlaceAfterDayOne() {
        LocalDate firstDate = LocalDate.of(2026, 7, 20);
        LocalDate secondDate = LocalDate.of(2026, 7, 21);
        CourseSavePlaceDto hotel = place(
                100L,
                firstDate,
                2,
                0,
                2.0,
                20.0
        );
        hotel.setCategory("HOTEL");
        CourseSavePlaceDto secondDayFirstPlace = place(
                20L,
                secondDate,
                1,
                60,
                7.6,
                34.0
        );
        secondDayFirstPlace.setTransitPathType(TransitPathType.BUS);
        secondDayFirstPlace.setRouteEstimated(true);
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .memberId(1L)
                .title("숙소 출발 2일 코스")
                .places(List.of(
                        place(10L, firstDate, 1, 90, 0.0, 0.0),
                        hotel,
                        secondDayFirstPlace
                ))
                .build();
        when(travelCourseRepository.save(any(TravelCourse.class)))
                .thenReturn(TravelCourse.builder()
                        .courseId(31L)
                        .title("숙소 출발 2일 코스")
                        .build());

        courseSaveService.saveOptimizedCourse(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<CourseDetail>> detailCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(courseDetailRepository).saveAll(detailCaptor.capture());
        List<CourseDetail> details = StreamSupport
                .stream(detailCaptor.getValue().spliterator(), false)
                .toList();
        CourseDetail savedSecondDayFirstPlace = details.get(2);

        assertEquals(2, savedSecondDayFirstPlace.getDayNo());
        assertEquals(1, savedSecondDayFirstPlace.getPlaceOrder());
        assertEquals(7.6,
                savedSecondDayFirstPlace.getDistanceFromPreviousKm(),
                0.000001);
        assertEquals(34.0,
                savedSecondDayFirstPlace.getTravelTimeFromPreviousMinutes(),
                0.000001);
        assertEquals(TransitPathType.BUS,
                savedSecondDayFirstPlace.getTransitPathType());
        assertEquals(true, savedSecondDayFirstPlace.getRouteEstimated());
    }

    @Test
    @DisplayName("DAY 1 첫 장소에는 이전 이동 경로를 저장할 수 없다")
    void rejectRouteMetadataOnFirstDayFirstPlace() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSavePlaceDto firstPlace = place(
                10L,
                visitDate,
                1,
                90,
                1.0,
                10.0
        );
        firstPlace.setTransitPathType(TransitPathType.BUS);
        firstPlace.setRouteEstimated(true);
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.PUBLIC_TRANSIT)
                .memberId(1L)
                .title("잘못된 첫 구간 코스")
                .places(List.of(firstPlace))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> courseSaveService.saveOptimizedCourse(request)
        );
        verify(travelCourseRepository, never()).save(any());
        verify(courseDetailRepository, never()).saveAll(any());
    }


    @Test
    @DisplayName("선택한 복수 코스를 하나의 요청으로 모두 저장한다")
    void saveOptimizedCourses() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSaveRequest first = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .resultId(101L)
                .title("취향 집중 코스")
                .courseType("SURVEY")
                .places(List.of(place(10L, visitDate, 1, 90, 0.0, 0.0)))
                .build();
        CourseSaveRequest second = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .resultId(101L)
                .title("이동 최소 코스")
                .courseType("SURVEY")
                .places(List.of(place(11L, visitDate, 1, 90, 0.0, 0.0)))
                .build();
        AtomicLong sequence = new AtomicLong(20L);
        when(travelCourseRepository.save(any(TravelCourse.class)))
                .thenAnswer(invocation -> {
                    TravelCourse course = invocation.getArgument(0);
                    return TravelCourse.builder()
                            .courseId(sequence.getAndIncrement())
                            .title(course.getTitle())
                            .build();
                });

        CourseBatchSaveResponse response = courseSaveService.saveOptimizedCourses(
                CourseBatchSaveRequest.builder()
                        .courses(List.of(first, second))
                        .build()
        );

        assertEquals(2, response.getSavedCount());
        assertEquals(List.of(20L, 21L), response.getSavedCourses().stream()
                .map(CourseSaveResponse::getCourseId)
                .toList());
        verify(travelCourseRepository, times(2)).save(any(TravelCourse.class));
        verify(courseDetailRepository, times(2)).saveAll(any());
    }

    @Test
    @DisplayName("복수 저장 요청에 다른 회원의 코스가 섞이면 저장하지 않는다")
    void rejectBatchWithDifferentMembers() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSaveRequest first = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .title("첫 번째 코스")
                .places(List.of(place(10L, visitDate, 1, 90, 0.0, 0.0)))
                .build();
        CourseSaveRequest second = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(2L)
                .title("두 번째 코스")
                .places(List.of(place(11L, visitDate, 1, 90, 0.0, 0.0)))
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseSaveService.saveOptimizedCourses(
                        CourseBatchSaveRequest.builder()
                                .courses(List.of(first, second))
                                .build()
                )
        );

        assertEquals(
                "복수 저장 요청의 모든 코스는 같은 회원 ID여야 합니다.",
                exception.getMessage()
        );
        verify(travelCourseRepository, never()).save(any());
        verify(courseDetailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("한 추천의 복수 저장 요청에 다른 이동수단이 섞이면 저장하지 않는다")
    void rejectBatchWithDifferentTransportModes() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSaveRequest walking = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .title("도보 코스")
                .places(List.of(place(10L, visitDate, 1, 90, 0.0, 0.0)))
                .build();
        CourseSaveRequest driving = CourseSaveRequest.builder()
                .transportMode(TransportMode.DRIVING)
                .memberId(1L)
                .title("자동차 코스")
                .places(List.of(place(11L, visitDate, 1, 90, 0.0, 0.0)))
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseSaveService.saveOptimizedCourses(
                        CourseBatchSaveRequest.builder()
                                .courses(List.of(walking, driving))
                                .build()
                )
        );

        assertTrue(exception.getMessage().contains("같은 이동수단"));
        verify(travelCourseRepository, never()).save(any());
        verify(courseDetailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("이동수단이 없으면 코스를 저장하지 않는다")
    void rejectMissingTransportMode() {
        CourseSaveRequest request = CourseSaveRequest.builder()
                .memberId(1L)
                .title("이동수단 없는 코스")
                .places(List.of(place(
                        1L,
                        LocalDate.of(2026, 7, 20),
                        1,
                        90,
                        0.0,
                        0.0
                )))
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseSaveService.saveOptimizedCourse(request)
        );

        assertTrue(exception.getMessage().contains("이동수단"));
        verify(travelCourseRepository, never()).save(any());
        verify(courseDetailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("설문 추천 코스에 결과 ID가 없으면 저장하지 않는다")
    void rejectSurveyCourseWithoutResultId() {
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .title("설문 결과 없는 추천 코스")
                .courseType("SURVEY")
                .places(List.of(place(
                        1L,
                        LocalDate.of(2026, 7, 20),
                        1,
                        90,
                        0.0,
                        0.0
                )))
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseSaveService.saveOptimizedCourse(request)
        );

        assertTrue(exception.getMessage().contains("설문 결과 ID"));
        verify(travelCourseRepository, never()).save(any());
        verify(courseDetailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("복수 코스 저장 메서드도 하나의 트랜잭션으로 처리한다")
    void batchSaveMethodIsTransactional() throws NoSuchMethodException {
        boolean transactional = CourseSaveService.class
                .getMethod("saveOptimizedCourses", CourseBatchSaveRequest.class)
                .isAnnotationPresent(Transactional.class);

        assertTrue(transactional);
    }

    @Test
    @DisplayName("날짜별 방문 순서가 1부터 이어지지 않으면 저장하지 않는다")
    void rejectInvalidVisitOrder() {
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .title("잘못된 코스")
                .places(List.of(place(
                        1L,
                        LocalDate.of(2026, 7, 20),
                        2,
                        90,
                        0.0,
                        0.0
                )))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> courseSaveService.saveOptimizedCourse(request)
        );

        verify(travelCourseRepository, never()).save(any());
        verify(courseDetailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("같은 장소가 여러 날짜에 중복되면 저장하지 않는다")
    void rejectDuplicatePlace() {
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .title("중복 장소 코스")
                .places(List.of(
                        place(1L, LocalDate.of(2026, 7, 20),
                                1, 90, 0.0, 0.0),
                        place(1L, LocalDate.of(2026, 7, 21),
                                1, 90, 0.0, 0.0)
                ))
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> courseSaveService.saveOptimizedCourse(request)
        );

        assertEquals(
                "동일한 장소를 코스에 중복 저장할 수 없습니다. placeId=1",
                exception.getMessage()
        );
        verify(travelCourseRepository, never()).save(any());
        verify(courseDetailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("같은 숙소는 서로 다른 날짜 마지막에 반복 저장할 수 있다")
    void allowSameHotelOnDifferentDates() {
        LocalDate firstDay = LocalDate.of(2026, 7, 20);
        LocalDate secondDay = LocalDate.of(2026, 7, 21);
        CourseSavePlaceDto firstHotel =
                place(100L, firstDay, 2, 30, 1.0, 10.0);
        firstHotel.setCategory("HOTEL");
        CourseSavePlaceDto secondHotel =
                place(100L, secondDay, 2, 30, 1.2, 12.0);
        secondHotel.setCategory("HOTEL");
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .title("같은 숙소를 쓰는 2일 코스")
                .places(List.of(
                        place(1L, firstDay, 1, 90, 0.0, 0.0),
                        firstHotel,
                        place(2L, secondDay, 1, 90, 0.0, 0.0),
                        secondHotel
                ))
                .build();
        when(travelCourseRepository.save(any(TravelCourse.class)))
                .thenReturn(TravelCourse.builder()
                        .courseId(40L)
                        .title("같은 숙소를 쓰는 2일 코스")
                        .build());

        CourseSaveResponse response =
                courseSaveService.saveOptimizedCourse(request);

        assertEquals(4, response.getPlaceCount());
        assertEquals(2, response.getDayCount());
        assertEquals(180, response.getTotalVisitTimeMinutes());
        assertEquals(202.0, response.getTotalCourseTimeMinutes(), 0.000001);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<CourseDetail>> detailCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(courseDetailRepository).saveAll(detailCaptor.capture());
        List<CourseDetail> details = StreamSupport
                .stream(detailCaptor.getValue().spliterator(), false)
                .toList();
        List<CourseDetail> hotelDetails = details.stream()
                .filter(detail -> detail.getPlaceId().equals(100L))
                .toList();
        assertEquals(2, hotelDetails.size());
        assertTrue(hotelDetails.stream()
                .allMatch(detail -> detail.getStayMinutes() == 0));
    }

    @Test
    @DisplayName("같은 숙소라도 같은 날짜에 두 번 저장할 수 없다")
    void rejectSameHotelTwiceOnSameDate() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSavePlaceDto firstHotel =
                place(100L, visitDate, 2, 30, 1.0, 10.0);
        firstHotel.setCategory("HOTEL");
        CourseSavePlaceDto duplicateHotel =
                place(100L, visitDate, 3, 30, 0.0, 0.0);
        duplicateHotel.setCategory("HOTEL");
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .title("같은 날 숙소 중복 코스")
                .places(List.of(
                        place(1L, visitDate, 1, 90, 0.0, 0.0),
                        firstHotel,
                        duplicateHotel
                ))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> courseSaveService.saveOptimizedCourse(request)
        );
        verify(travelCourseRepository, never()).save(any());
        verify(courseDetailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("상세 장소 저장 중 오류가 발생하면 예외를 전파해 트랜잭션 롤백되게 한다")
    void propagateDetailSaveFailureForRollback() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSaveRequest request = CourseSaveRequest.builder()
                .transportMode(TransportMode.WALKING)
                .memberId(1L)
                .title("저장 실패 테스트 코스")
                .places(List.of(place(
                        1L,
                        visitDate,
                        1,
                        90,
                        0.0,
                        0.0
                )))
                .build();
        when(travelCourseRepository.save(any(TravelCourse.class)))
                .thenReturn(TravelCourse.builder()
                        .courseId(10L)
                        .title("저장 실패 테스트 코스")
                        .build());
        when(courseDetailRepository.saveAll(any()))
                .thenThrow(new IllegalStateException("상세 장소 저장 실패"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> courseSaveService.saveOptimizedCourse(request)
        );

        assertEquals("상세 장소 저장 실패", exception.getMessage());
        verify(travelCourseRepository).save(any(TravelCourse.class));
        verify(courseDetailRepository).saveAll(any());
    }

    @Test
    @DisplayName("코스와 상세 장소 저장은 하나의 트랜잭션으로 처리한다")
    void saveMethodIsTransactional() throws NoSuchMethodException {
        boolean transactional = CourseSaveService.class
                .getMethod("saveOptimizedCourse", CourseSaveRequest.class)
                .isAnnotationPresent(Transactional.class);

        assertTrue(transactional);
    }

    /** 날짜·순서별 상세 장소 입력을 간결하게 만드는 테스트 헬퍼이다. */
    private CourseSavePlaceDto place(
            Long placeId,
            LocalDate visitDate,
            Integer visitOrder,
            Integer expectedVisitMinutes,
            Double distanceFromPreviousKm,
            Double travelTimeFromPreviousMinutes
    ) {
        return CourseSavePlaceDto.builder()
                .placeId(placeId)
                .visitDate(visitDate)
                .visitOrder(visitOrder)
                .expectedVisitMinutes(expectedVisitMinutes)
                .distanceFromPreviousKm(distanceFromPreviousKm)
                .travelTimeFromPreviousMinutes(travelTimeFromPreviousMinutes)
                .build();
    }
}
