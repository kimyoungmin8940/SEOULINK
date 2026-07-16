package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseSavePlaceDto;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.entity.CourseDetail;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        assertEquals(10L, response.getCourseId());
        assertEquals(3, response.getPlaceCount());
        assertEquals(2, response.getDayCount());
        assertEquals(0.267, response.getTotalDistanceKm(), 0.000001);
        assertEquals(3.56, response.getTotalTravelTimeMinutes(), 0.000001);
        assertEquals(240, response.getTotalVisitTimeMinutes());
        assertEquals(243.56, response.getTotalCourseTimeMinutes(), 0.000001);
    }

    @Test
    @DisplayName("날짜별 방문 순서가 1부터 이어지지 않으면 저장하지 않는다")
    void rejectInvalidVisitOrder() {
        CourseSaveRequest request = CourseSaveRequest.builder()
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
    @DisplayName("상세 장소 저장 중 오류가 발생하면 예외를 전파해 트랜잭션 롤백되게 한다")
    void propagateDetailSaveFailureForRollback() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        CourseSaveRequest request = CourseSaveRequest.builder()
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
