package com.seoulink.backend.domain.course.controller;

import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import com.seoulink.backend.domain.course.service.CourseOptimizationService;
import com.seoulink.backend.domain.course.service.CourseRecommendationService;
import com.seoulink.backend.domain.course.service.CourseSaveService;
import com.seoulink.backend.domain.course.service.CourseService;
import com.seoulink.backend.domain.course.service.DistanceService;
import com.seoulink.backend.domain.course.service.VisitDurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 코스 최적화·추천 저장·직접 저장·상세 조회 API의 HTTP 상태와 JSON 형식을 검증한다.
 * 서비스 로직은 Mockito로 분리하고 컨트롤러의 요청 매핑과 예외 변환에 집중한다.
 */
class CourseControllerTest {

    private MockMvc mockMvc;
    private CourseRecommendationService courseRecommendationService;
    private CourseSaveService courseSaveService;
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        // Spring 전체 컨텍스트 없이 컨트롤러와 Jackson 변환만 빠르게 검증한다.
        DistanceService distanceService = new DistanceService(null);
        CourseOptimizationService optimizationService =
                new CourseOptimizationService(
                        distanceService,
                        new VisitDurationService()
                );
        courseSaveService = mock(CourseSaveService.class);
        courseRecommendationService = mock(CourseRecommendationService.class);
        courseService = mock(CourseService.class);

        mockMvc = standaloneSetup(new CourseController(
                        optimizationService,
                        courseRecommendationService,
                        courseSaveService,
                        courseService
                ))
                .build();
    }

    @Test
    @DisplayName("HTTP 요청으로 코스 최적화 결과를 반환한다")
    void optimizeCourse() throws Exception {
        mockMvc.perform(post("/api/courses/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "placeCandidates": [
                                    {
                                      "placeId": 3,
                                      "placeName": "경복궁",
                                      "category": "관광지",
                                      "recommendationScore": 90.0,
                                      "latitude": 37.5796,
                                      "longitude": 126.9770,
                                      "visitDate": "2026-07-20"
                                    },
                                    {
                                      "placeId": 1,
                                      "placeName": "서울시청",
                                      "category": "관광지",
                                      "recommendationScore": 100.0,
                                      "latitude": 37.5665,
                                      "longitude": 126.9780,
                                      "visitDate": "2026-07-20"
                                    },
                                    {
                                      "placeId": 2,
                                      "placeName": "덕수궁",
                                      "category": "관광지",
                                      "recommendationScore": 70.0,
                                      "latitude": 37.5658,
                                      "longitude": 126.9751,
                                      "visitDate": "2026-07-20"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optimizedPlaces.length()").value(3))
                .andExpect(jsonPath("$.optimizedPlaces[0].placeId").value(1))
                .andExpect(jsonPath("$.optimizedPlaces[0].visitOrder").value(1))
                .andExpect(jsonPath("$.optimizedPlaces[0].expectedVisitMinutes").value(90))
                .andExpect(jsonPath("$.optimizedPlaces[1].placeId").value(2))
                .andExpect(jsonPath("$.optimizedPlaces[1].visitOrder").value(2))
                .andExpect(jsonPath("$.optimizedPlaces[1].expectedVisitMinutes").value(90))
                .andExpect(jsonPath("$.optimizedPlaces[2].placeId").value(3))
                .andExpect(jsonPath("$.optimizedPlaces[2].visitOrder").value(3))
                .andExpect(jsonPath("$.optimizedPlaces[2].expectedVisitMinutes").value(90))
                .andExpect(jsonPath("$.totalDistanceKm").isNumber())
                .andExpect(jsonPath("$.totalTravelTimeMinutes").isNumber())
                .andExpect(jsonPath("$.totalVisitTimeMinutes").value(270))
                .andExpect(jsonPath("$.totalCourseTimeMinutes").isNumber());
    }

    @Test
    @DisplayName("필수 입력값이 없으면 400 응답을 반환한다")
    void rejectInvalidCourseRequest() throws Exception {
        mockMvc.perform(post("/api/courses/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "placeCandidates": [
                                    {
                                      "placeId": 1,
                                      "placeName": "날짜 없는 장소",
                                      "category": "관광지",
                                      "recommendationScore": 90.0,
                                      "latitude": 37.5665,
                                      "longitude": 126.9780
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("방문 날짜는 필수입니다."));
    }

    @Test
    @DisplayName("확정한 최적화 코스를 저장하고 201 응답을 반환한다")
    void saveCourse() throws Exception {
        when(courseSaveService.saveOptimizedCourse(any()))
                .thenReturn(CourseSaveResponse.builder()
                        .courseId(10L)
                        .title("서울 궁궐 코스")
                        .placeCount(3)
                        .dayCount(1)
                        .totalDistanceKm(2.63)
                        .totalTravelTimeMinutes(31.67)
                        .totalVisitTimeMinutes(270)
                        .totalCourseTimeMinutes(301.67)
                        .build());

        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": 1,
                                  "title": "서울 궁궐 코스",
                                  "description": "서울 궁궐을 둘러보는 코스",
                                  "travelCode": "ATLSR",
                                  "courseType": "SURVEY",
                                  "region": "서울 종로구",
                                  "publicCourse": false,
                                  "places": [
                                    {
                                      "placeId": 1,
                                      "visitDate": "2026-07-20",
                                      "visitOrder": 1,
                                      "expectedVisitMinutes": 90,
                                      "distanceFromPreviousKm": 0.0,
                                      "travelTimeFromPreviousMinutes": 0.0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(10))
                .andExpect(jsonPath("$.title").value("서울 궁궐 코스"))
                .andExpect(jsonPath("$.placeCount").value(3))
                .andExpect(jsonPath("$.dayCount").value(1))
                .andExpect(jsonPath("$.totalDistanceKm").value(2.63))
                .andExpect(jsonPath("$.totalTravelTimeMinutes").value(31.67))
                .andExpect(jsonPath("$.totalVisitTimeMinutes").value(270))
                .andExpect(jsonPath("$.totalCourseTimeMinutes").value(301.67));
    }

    @Test
    @DisplayName("추천 후보를 최적화하고 저장한 뒤 201 응답을 반환한다")
    void recommendCourse() throws Exception {
        when(courseRecommendationService.recommendAndSave(any()))
                .thenReturn(CourseRecommendResponse.builder()
                        .courseId(20L)
                        .title("서울 추천 코스")
                        .placeCount(1)
                        .dayCount(1)
                        .totalDistanceKm(0.0)
                        .totalTravelTimeMinutes(0.0)
                        .totalVisitTimeMinutes(90)
                        .totalCourseTimeMinutes(90.0)
                        .optimizedPlaces(List.of(OptimizedPlaceDto.builder()
                                .placeId(1L)
                                .placeName("경복궁")
                                .visitOrder(1)
                                .expectedVisitMinutes(90)
                                .build()))
                        .build());

        mockMvc.perform(post("/api/courses/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": 1,
                                  "title": "서울 추천 코스",
                                  "travelCode": "ATLSR",
                                  "placeCandidates": [
                                    {
                                      "placeId": 1,
                                      "placeName": "경복궁",
                                      "category": "TOUR",
                                      "recommendationScore": 92.0,
                                      "latitude": 37.5796,
                                      "longitude": 126.9770,
                                      "visitDate": "2026-07-20"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(20))
                .andExpect(jsonPath("$.title").value("서울 추천 코스"))
                .andExpect(jsonPath("$.optimizedPlaces.length()").value(1))
                .andExpect(jsonPath("$.optimizedPlaces[0].placeId").value(1))
                .andExpect(jsonPath("$.totalCourseTimeMinutes").value(90.0));
    }

    @Test
    @DisplayName("저장된 코스 상세정보를 조회한다")
    void getCourse() throws Exception {
        when(courseService.getCourse(10L))
                .thenReturn(CourseDetailResponse.builder()
                        .courseId(10L)
                        .title("서울 궁궐 코스")
                        .region("서울 종로구")
                        .publicCourse(true)
                        .placeCount(1)
                        .dayCount(1)
                        .places(List.of(CoursePlaceResponse.builder()
                                .detailId(100L)
                                .placeId(1L)
                                .dayNo(1)
                                .visitOrder(1)
                                .expectedVisitMinutes(90)
                                .build()))
                        .build());

        mockMvc.perform(get("/api/courses/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(10))
                .andExpect(jsonPath("$.title").value("서울 궁궐 코스"))
                .andExpect(jsonPath("$.placeCount").value(1))
                .andExpect(jsonPath("$.dayCount").value(1))
                .andExpect(jsonPath("$.places[0].placeId").value(1))
                .andExpect(jsonPath("$.places[0].visitOrder").value(1));
    }

    @Test
    @DisplayName("회원의 설문 추천 코스 목록을 조회한다")
    void getRecommendedCourses() throws Exception {
        when(courseService.getRecommendedCourses(1L))
                .thenReturn(List.of(CourseRecommendationResponse.builder()
                        .courseId(20L)
                        .title("서울 추천 코스")
                        .placeCount(3)
                        .dayCount(1)
                        .build()));

        mockMvc.perform(get("/api/courses/recommended")
                        .param("memberId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseId").value(20))
                .andExpect(jsonPath("$[0].title").value("서울 추천 코스"))
                .andExpect(jsonPath("$[0].placeCount").value(3));
    }
}
