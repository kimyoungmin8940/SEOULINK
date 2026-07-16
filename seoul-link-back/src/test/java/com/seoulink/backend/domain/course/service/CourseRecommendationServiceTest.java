package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 추천 후보가 최적화 DTO를 거쳐 SURVEY 코스로 저장되는 통합 서비스 흐름을 검증한다. */
class CourseRecommendationServiceTest {

    private CourseOptimizationService courseOptimizationService;
    private CourseSaveService courseSaveService;
    private CourseRecommendationService courseRecommendationService;

    @BeforeEach
    void setUp() {
        // 최적화와 저장 서비스를 mock으로 분리해 두 서비스 사이의 전달값을 확인한다.
        courseOptimizationService = mock(CourseOptimizationService.class);
        courseSaveService = mock(CourseSaveService.class);
        courseRecommendationService = new CourseRecommendationService(
                courseOptimizationService,
                courseSaveService
        );
    }

    @Test
    @DisplayName("추천 후보를 최적화하고 SURVEY 코스로 저장한다")
    void recommendAndSave() {
        LocalDate visitDate = LocalDate.of(2026, 7, 20);
        PlaceCandidateDto candidate = PlaceCandidateDto.builder()
                .placeId(1L)
                .placeName("경복궁")
                .category("TOUR")
                .recommendationScore(92.0)
                .latitude(37.5796)
                .longitude(126.9770)
                .visitDate(visitDate)
                .build();
        PlaceCandidateDto alternativeCandidate = PlaceCandidateDto.builder()
                .placeId(2L)
                .placeName("덕수궁")
                .category("TOUR")
                .recommendationScore(88.0)
                .latitude(37.5658)
                .longitude(126.9751)
                .visitDate(visitDate)
                .build();
        OptimizedPlaceDto optimizedPlace = OptimizedPlaceDto.builder()
                .placeId(1L)
                .placeName("경복궁")
                .category("TOUR")
                .recommendationScore(92.0)
                .latitude(37.5796)
                .longitude(126.9770)
                .visitDate(visitDate)
                .expectedVisitMinutes(90)
                .visitOrder(1)
                .distanceFromPreviousKm(0.0)
                .travelTimeFromPreviousMinutes(0.0)
                .build();

        when(courseOptimizationService.optimize(any(CourseOptimizeRequest.class)))
                .thenReturn(CourseOptimizeResponse.builder()
                        .optimizedPlaces(List.of(optimizedPlace))
                        .totalDistanceKm(0.0)
                        .totalTravelTimeMinutes(0.0)
                        .totalVisitTimeMinutes(90)
                        .totalCourseTimeMinutes(90.0)
                        .build());
        when(courseSaveService.saveOptimizedCourse(any(CourseSaveRequest.class)))
                .thenReturn(CourseSaveResponse.builder()
                        .courseId(20L)
                        .title("서울 추천 코스")
                        .placeCount(1)
                        .dayCount(1)
                        .totalDistanceKm(0.0)
                        .totalTravelTimeMinutes(0.0)
                        .totalVisitTimeMinutes(90)
                        .totalCourseTimeMinutes(90.0)
                        .build());

        CourseRecommendResponse response =
                courseRecommendationService.recommendAndSave(
                        CourseRecommendRequest.builder()
                                .memberId(1L)
                                .resultId(5L)
                                .title("서울 추천 코스")
                                .travelCode("ATLSR")
                                .region("서울 종로구")
                                .placeCandidates(List.of(candidate))
                                .alternativeCandidates(List.of(alternativeCandidate))
                                .build()
                );

        ArgumentCaptor<CourseOptimizeRequest> optimizeCaptor =
                ArgumentCaptor.forClass(CourseOptimizeRequest.class);
        verify(courseOptimizationService).optimize(optimizeCaptor.capture());
        ArgumentCaptor<CourseSaveRequest> saveCaptor =
                ArgumentCaptor.forClass(CourseSaveRequest.class);
        verify(courseSaveService).saveOptimizedCourse(saveCaptor.capture());
        CourseSaveRequest saveRequest = saveCaptor.getValue();

        assertEquals(1L, saveRequest.getMemberId());
        assertEquals(5L, saveRequest.getResultId());
        assertEquals(1, optimizeCaptor.getValue().getAlternativeCandidates().size());
        assertEquals(
                2L,
                optimizeCaptor.getValue().getAlternativeCandidates().get(0).getPlaceId()
        );
        assertEquals("SURVEY", saveRequest.getCourseType());
        assertEquals(1, saveRequest.getPlaces().size());
        assertEquals(1L, saveRequest.getPlaces().get(0).getPlaceId());
        assertEquals(1, saveRequest.getPlaces().get(0).getVisitOrder());
        assertEquals(20L, response.getCourseId());
        assertEquals("ATLSR", response.getTravelCode());
        assertEquals("SURVEY", response.getCourseType());
        assertEquals("서울 종로구", response.getRegion());
        assertEquals(1, response.getDays().size());
        assertEquals(1, response.getDays().get(0).getDayNo());
        assertEquals(visitDate, response.getDays().get(0).getVisitDate());
        assertEquals(0.0, response.getDays().get(0).getDailyDistanceKm(), 0.000001);
        assertEquals(
                0.0,
                response.getDays().get(0).getDailyTravelTimeMinutes(),
                0.000001
        );
        assertEquals(90, response.getDays().get(0).getDailyVisitTimeMinutes());
        assertEquals(
                90.0,
                response.getDays().get(0).getDailyCourseTimeMinutes(),
                0.000001
        );
        assertEquals(1L, response.getDays().get(0).getPlaces().get(0).getPlaceId());
        assertEquals(
                92.0,
                response.getDays().get(0).getPlaces().get(0).getRecommendationScore(),
                0.000001
        );
        assertEquals(
                90.0,
                response.getTotalCourseTimeMinutes(),
                0.000001
        );
    }

    @Test
    @DisplayName("최적화가 실패하면 코스 저장을 실행하지 않는다")
    void doNotSaveWhenOptimizationFails() {
        when(courseOptimizationService.optimize(any(CourseOptimizeRequest.class)))
                .thenThrow(new IllegalArgumentException("장소의 위도와 경도는 필수입니다."));

        CourseRecommendRequest request = CourseRecommendRequest.builder()
                .memberId(1L)
                .title("실패 코스")
                .placeCandidates(List.of(PlaceCandidateDto.builder().build()))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> courseRecommendationService.recommendAndSave(request)
        );
        verify(courseSaveService, never()).saveOptimizedCourse(any());
    }

    @Test
    @DisplayName("추천 생성과 저장은 하나의 트랜잭션으로 처리한다")
    void recommendMethodIsTransactional() throws NoSuchMethodException {
        boolean transactional = CourseRecommendationService.class
                .getMethod("recommendAndSave", CourseRecommendRequest.class)
                .isAnnotationPresent(Transactional.class);

        assertTrue(transactional);
    }
}
