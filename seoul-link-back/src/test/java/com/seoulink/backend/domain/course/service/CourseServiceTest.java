package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
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

/** 저장 엔티티가 상세 응답과 목록 카드 응답으로 올바르게 변환되는지 검증한다. */
class CourseServiceTest {

    private TravelCourseRepository travelCourseRepository;
    private CourseDetailRepository courseDetailRepository;
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        // 조회 순서와 응답 변환만 확인할 수 있도록 Repository를 mock으로 구성한다.
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
                detail(101L, 10L, 2L, 1, 2, visitDate, 90, 0.27, 3.56),
                detail(102L, 10L, 3L, 2, 1, visitDate.plusDays(1), 90, 0.0, 0.0)
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
        assertEquals(List.of(1, 2), response.getDays().get(0).getPlaces().stream()
                .map(place -> place.getVisitOrder())
                .toList());
        assertEquals(
                90,
                response.getDays().get(0).getPlaces().get(0).getExpectedVisitMinutes()
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
    @DisplayName("회원의 SURVEY 추천 코스만 최신순으로 조회한다")
    void getRecommendedCourses() {
        TravelCourse recommended = TravelCourse.builder()
                .courseId(20L)
                .memberId(1L)
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

        List<CourseRecommendationResponse> response =
                courseService.getRecommendedCourses(1L);

        assertEquals(1, response.size());
        assertEquals(20L, response.get(0).getCourseId());
        assertEquals(2, response.get(0).getPlaceCount());
        assertEquals(2, response.get(0).getDayCount());
        assertEquals(List.of("서울 종로구"), response.get(0).getRegions());
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
        assertEquals(1, response.get(0).getPlaceCount());
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
