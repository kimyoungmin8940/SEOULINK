package com.seoulink.backend.domain.course.controller;

import com.seoulink.backend.domain.course.dto.response.CourseBatchSaveResponse;
import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptionResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
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
import java.util.NoSuchElementException;

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
    @DisplayName("HTTP 요청의 대체 후보로 먼 장소를 교체한 결과를 반환한다")
    void optimizeCourseWithAlternativeCandidates() throws Exception {
        mockMvc.perform(post("/api/courses/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "placeCandidates": [
                                    {
                                      "placeId": 1,
                                      "placeName": "서울시청",
                                      "category": "TOUR",
                                      "recommendationScore": 100.0,
                                      "latitude": 37.5665,
                                      "longitude": 126.9780,
                                      "visitDate": "2026-07-20"
                                    },
                                    {
                                      "placeId": 2,
                                      "placeName": "먼 관광지",
                                      "category": "TOUR",
                                      "recommendationScore": 90.0,
                                      "latitude": 37.5854,
                                      "longitude": 126.9780,
                                      "visitDate": "2026-07-20"
                                    }
                                  ],
                                  "alternativeCandidates": [
                                    {
                                      "placeId": 3,
                                      "placeName": "덕수궁",
                                      "category": "관광지",
                                      "recommendationScore": 85.0,
                                      "latitude": 37.5658,
                                      "longitude": 126.9751,
                                      "visitDate": "2026-07-20"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optimizedPlaces.length()").value(2))
                .andExpect(jsonPath("$.optimizedPlaces[0].placeId").value(1))
                .andExpect(jsonPath("$.optimizedPlaces[1].placeId").value(3))
                .andExpect(jsonPath("$.optimizedPlaces[1].visitOrder").value(2))
                .andExpect(jsonPath("$.totalDistanceKm").value(
                        org.hamcrest.Matchers.lessThan(2.0)
                ))
                .andExpect(jsonPath("$.totalTravelTimeMinutes").value(
                        org.hamcrest.Matchers.lessThan(30.0)
                ));
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
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
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
    @DisplayName("선택한 복수 코스를 배치 저장한다")
    void saveCourses() throws Exception {
        when(courseSaveService.saveOptimizedCourses(any()))
                .thenReturn(CourseBatchSaveResponse.builder()
                        .savedCount(2)
                        .savedCourses(List.of(
                                CourseSaveResponse.builder()
                                        .courseId(20L)
                                        .title("취향 집중 코스")
                                        .placeCount(1)
                                        .dayCount(1)
                                        .build(),
                                CourseSaveResponse.builder()
                                        .courseId(21L)
                                        .title("이동 최소 코스")
                                        .placeCount(1)
                                        .dayCount(1)
                                        .build()
                        ))
                        .build());

        mockMvc.perform(post("/api/courses/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courses": [
                                    {
                                      "memberId": 1,
                                      "resultId": 101,
                                      "title": "취향 집중 코스",
                                      "courseType": "SURVEY",
                                      "places": [
                                        {
                                          "placeId": 10,
                                          "visitDate": "2026-07-20",
                                          "visitOrder": 1,
                                          "expectedVisitMinutes": 90,
                                          "distanceFromPreviousKm": 0.0,
                                          "travelTimeFromPreviousMinutes": 0.0
                                        }
                                      ]
                                    },
                                    {
                                      "memberId": 1,
                                      "resultId": 101,
                                      "title": "이동 최소 코스",
                                      "courseType": "SURVEY",
                                      "places": [
                                        {
                                          "placeId": 11,
                                          "visitDate": "2026-07-20",
                                          "visitOrder": 1,
                                          "expectedVisitMinutes": 90,
                                          "distanceFromPreviousKm": 0.0,
                                          "travelTimeFromPreviousMinutes": 0.0
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.savedCount").value(2))
                .andExpect(jsonPath("$.savedCourses[0].courseId").value(20))
                .andExpect(jsonPath("$.savedCourses[1].courseId").value(21));
    }

    @Test
    @DisplayName("최종 후보 풀을 받아 추천 코스 3개를 반환한다")
    void recommendCourse() throws Exception {
        CourseDayResponse day = CourseDayResponse.builder()
                .dayNo(1)
                .visitDate(java.time.LocalDate.of(2026, 7, 20))
                .dailyDistanceKm(1.2)
                .dailyTravelTimeMinutes(16.0)
                .dailyVisitTimeMinutes(300)
                .dailyCourseTimeMinutes(316.0)
                .places(List.of(CoursePlaceResponse.builder()
                        .placeId(10L)
                        .placeName("경복궁")
                        .category("TOUR")
                        .recommendationScore(92.0)
                        .themePalaceCultureYn("Y")
                        .visitOrder(1)
                        .visitTime("10:00")
                        .expectedVisitMinutes(90)
                        .build()))
                .build();
        when(courseRecommendationService.recommend(any()))
                .thenReturn(CourseRecommendResponse.builder()
                        .resultId(101L)
                        .dailyStartTime(java.time.LocalTime.of(10, 0))
                        .weatherStatus("RAIN")
                        .temperature(27.5)
                        .rainProbability(80)
                        .optionCount(3)
                        .courseOptions(List.of(
                                option(1, "PREFERENCE", "취향 집중 코스", day),
                                option(2, "MIN_DISTANCE", "이동 최소 코스", day),
                                option(3, "BALANCED", "균형 추천 코스", day)
                        ))
                        .build());

        mockMvc.perform(post("/api/courses/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resultId": 101,
                                  "dailyStartTime": "10:00",
                                  "weatherStatus": "RAIN",
                                  "temperature": 27.5,
                                  "rainProbability": 80,
                                  "dailyPlans": [
                                    {
                                      "visitDate": "2026-07-20",
                                      "targetPlaceCount": 4,
                                      "categoryTargets": {
                                        "TOUR": 2,
                                        "RESTAURANT": 1,
                                        "CAFE": 1,
                                        "HOTEL": 0
                                      },
                                      "placeCandidates": [
                                        {
                                          "placeId": 10,
                                          "placeName": "경복궁",
                                          "category": "TOUR",
                                          "recommendationScore": 92.0,
                                          "latitude": 37.5796,
                                          "longitude": 126.9770,
                                          "themePalaceCultureYn": "Y",
                                          "alternativeCandidates": []
                                        },
                                        {
                                          "placeId": 11,
                                          "placeName": "덕수궁",
                                          "category": "TOUR",
                                          "recommendationScore": 89.0,
                                          "latitude": 37.5658,
                                          "longitude": 126.9751,
                                          "alternativeCandidates": []
                                        },
                                        {
                                          "placeId": 12,
                                          "placeName": "청계천",
                                          "category": "TOUR",
                                          "recommendationScore": 86.0,
                                          "latitude": 37.5690,
                                          "longitude": 126.9784,
                                          "alternativeCandidates": []
                                        },
                                        {
                                          "placeId": 40,
                                          "placeName": "광화문 식당",
                                          "category": "RESTAURANT",
                                          "recommendationScore": 89.0,
                                          "latitude": 37.5701,
                                          "longitude": 126.9768,
                                          "alternativeCandidates": []
                                        },
                                        {
                                          "placeId": 42,
                                          "placeName": "종로 맛집",
                                          "category": "RESTAURANT",
                                          "recommendationScore": 84.0,
                                          "latitude": 37.5704,
                                          "longitude": 126.9921,
                                          "alternativeCandidates": []
                                        },
                                        {
                                          "placeId": 50,
                                          "placeName": "서촌 카페",
                                          "category": "CAFE",
                                          "recommendationScore": 87.0,
                                          "latitude": 37.5792,
                                          "longitude": 126.9693,
                                          "alternativeCandidates": []
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultId").value(101))
                .andExpect(jsonPath("$.dailyStartTime").value("10:00"))
                .andExpect(jsonPath("$.weatherStatus").value("RAIN"))
                .andExpect(jsonPath("$.temperature").value(27.5))
                .andExpect(jsonPath("$.rainProbability").value(80))
                .andExpect(jsonPath("$.optionCount").value(3))
                .andExpect(jsonPath("$.courseOptions.length()").value(3))
                .andExpect(jsonPath("$.courseOptions[0].optionType").value("PREFERENCE"))
                .andExpect(jsonPath("$.courseOptions[1].optionType").value("MIN_DISTANCE"))
                .andExpect(jsonPath("$.courseOptions[2].optionType").value("BALANCED"))
                .andExpect(jsonPath("$.courseOptions[0].days[0].visitDate")
                        .value("2026-07-20"))
                .andExpect(jsonPath("$.courseOptions[0].days[0].places[0].placeId")
                        .value(10))
                .andExpect(jsonPath("$.courseOptions[0].days[0].places[0].visitTime")
                        .value("10:00"));
    }

    private CourseOptionResponse option(
            int optionNo,
            String optionType,
            String optionName,
            CourseDayResponse day
    ) {
        return CourseOptionResponse.builder()
                .optionNo(optionNo)
                .optionType(optionType)
                .optionName(optionName)
                .placeCount(4)
                .dayCount(1)
                .totalDistanceKm(1.2)
                .totalTravelTimeMinutes(16.0)
                .totalVisitTimeMinutes(300)
                .totalCourseTimeMinutes(316.0)
                .days(List.of(day))
                .build();
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
                        .days(List.of(CourseDayResponse.builder()
                                .dayNo(1)
                                .visitDate(java.time.LocalDate.of(2026, 7, 20))
                                .dailyDistanceKm(0.0)
                                .dailyTravelTimeMinutes(0.0)
                                .dailyVisitTimeMinutes(90)
                                .dailyCourseTimeMinutes(90.0)
                                .places(List.of(CoursePlaceResponse.builder()
                                        .detailId(100L)
                                        .placeId(1L)
                                        .visitOrder(1)
                                        .expectedVisitMinutes(90)
                                        .build()))
                                .build()))
                        .build());

        mockMvc.perform(get("/api/courses/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(10))
                .andExpect(jsonPath("$.title").value("서울 궁궐 코스"))
                .andExpect(jsonPath("$.placeCount").value(1))
                .andExpect(jsonPath("$.dayCount").value(1))
                .andExpect(jsonPath("$.days[0].dayNo").value(1))
                .andExpect(jsonPath("$.days[0].visitDate").value("2026-07-20"))
                .andExpect(jsonPath("$.days[0].dailyDistanceKm").value(0.0))
                .andExpect(jsonPath("$.days[0].dailyTravelTimeMinutes").value(0.0))
                .andExpect(jsonPath("$.days[0].dailyVisitTimeMinutes").value(90))
                .andExpect(jsonPath("$.days[0].dailyCourseTimeMinutes").value(90.0))
                .andExpect(jsonPath("$.days[0].places[0].placeId").value(1))
                .andExpect(jsonPath("$.days[0].places[0].visitOrder").value(1));
    }

    @Test
    @DisplayName("존재하지 않는 코스는 코드가 포함된 404 응답을 반환한다")
    void getUnknownCourse() throws Exception {
        when(courseService.getCourse(999L))
                .thenThrow(new NoSuchElementException(
                        "코스를 찾을 수 없습니다. courseId=999"
                ));

        mockMvc.perform(get("/api/courses/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(
                        "코스를 찾을 수 없습니다. courseId=999"
                ));
    }

    @Test
    @DisplayName("코스 내부 처리 실패는 상세 원인을 숨긴 500 응답을 반환한다")
    void rejectCourseProcessingFailure() throws Exception {
        when(courseRecommendationService.recommend(any()))
                .thenThrow(new IllegalStateException("추천 최적화 실패"));

        mockMvc.perform(post("/api/courses/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COURSE_PROCESSING_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        "코스 처리 중 오류가 발생했습니다."
                ));
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
