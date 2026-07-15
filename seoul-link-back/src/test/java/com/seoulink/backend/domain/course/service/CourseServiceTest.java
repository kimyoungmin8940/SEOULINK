package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.entity.CourseDetail;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseServiceTest {

    private TravelCourseRepository travelCourseRepository;
    private CourseDetailRepository courseDetailRepository;
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        travelCourseRepository = mock(TravelCourseRepository.class);
        courseDetailRepository = mock(CourseDetailRepository.class);
        courseService = new CourseService(
                travelCourseRepository,
                courseDetailRepository
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
                .title("서울 궁궐 코스")
                .description("서울 궁궐을 둘러보는 코스")
                .travelCode("ATLSR")
                .courseType("SURVEY")
                .region("서울 종로구")
                .publicStatus("Y")
                .viewCount(12L)
                .totalDistanceKm(2.63)
                .totalTravelTimeMinutes(31.67)
                .totalVisitTimeMinutes(270)
                .totalCourseTimeMinutes(301.67)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        List<CourseDetail> details = List.of(
                detail(100L, 10L, 1L, 1, 1, visitDate, 90, 0.0, 0.0),
                detail(101L, 10L, 2L, 1, 2, visitDate, 90, 0.27, 3.56)
        );

        when(travelCourseRepository.findById(10L))
                .thenReturn(Optional.of(course));
        when(courseDetailRepository
                .findByCourseIdOrderByDayNoAscPlaceOrderAsc(10L))
                .thenReturn(details);

        CourseDetailResponse response = courseService.getCourse(10L);

        assertEquals(10L, response.getCourseId());
        assertEquals("서울 궁궐 코스", response.getTitle());
        assertEquals(true, response.getPublicCourse());
        assertEquals(2, response.getPlaceCount());
        assertEquals(1, response.getDayCount());
        assertEquals(List.of(1L, 2L), response.getPlaces().stream()
                .map(place -> place.getPlaceId())
                .toList());
        assertEquals(List.of(1, 2), response.getPlaces().stream()
                .map(place -> place.getVisitOrder())
                .toList());
        assertEquals(90, response.getPlaces().get(0).getExpectedVisitMinutes());
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
                .build();
    }
}
