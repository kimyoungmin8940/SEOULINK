package com.seoulink.backend.domain.course.controller;

import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
import com.seoulink.backend.domain.course.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** 로그인 연동 전 임시 회원 ID를 받는 내 코스 조회 API 계약을 검증한다. */
class MemberCourseControllerTest {

    private MockMvc mockMvc;
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        // 조회 결과는 서비스 mock으로 고정하고 URL·파라미터·응답 JSON만 확인한다.
        courseService = mock(CourseService.class);
        mockMvc = standaloneSetup(new MemberCourseController(courseService))
                .build();
    }

    @Test
    @DisplayName("로그인 연동 전 회원 ID로 내 코스 목록을 조회한다")
    void getMyCourses() throws Exception {
        when(courseService.getMemberCourses(1L))
                .thenReturn(List.of(CourseRecommendationResponse.builder()
                        .courseId(30L)
                        .title("내 서울 코스")
                        .placeCount(2)
                        .dayCount(1)
                        .build()));

        mockMvc.perform(get("/api/members/me/courses")
                        .param("memberId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseId").value(30))
                .andExpect(jsonPath("$[0].title").value("내 서울 코스"))
                .andExpect(jsonPath("$[0].placeCount").value(2));
    }

    @Test
    @DisplayName("잘못된 회원 ID는 코드가 포함된 400 응답을 반환한다")
    void rejectInvalidMemberId() throws Exception {
        when(courseService.getMemberCourses(0L))
                .thenThrow(new IllegalArgumentException(
                        "회원 ID는 1 이상이어야 합니다."
                ));

        mockMvc.perform(get("/api/members/me/courses")
                        .param("memberId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(
                        "회원 ID는 1 이상이어야 합니다."
                ));
    }
}
