package com.seoulink.backend.domain.course.controller;

import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.service.CourseOptimizationService;
import com.seoulink.backend.domain.course.service.CourseSaveService;
import com.seoulink.backend.domain.course.service.DistanceService;
import com.seoulink.backend.domain.course.service.VisitDurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CourseControllerTest {

    private MockMvc mockMvc;
    private CourseSaveService courseSaveService;

    @BeforeEach
    void setUp() {
        DistanceService distanceService = new DistanceService(null);
        CourseOptimizationService optimizationService =
                new CourseOptimizationService(
                        distanceService,
                        new VisitDurationService()
                );
        courseSaveService = mock(CourseSaveService.class);

        mockMvc = standaloneSetup(new CourseController(
                        optimizationService,
                        courseSaveService
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
}
