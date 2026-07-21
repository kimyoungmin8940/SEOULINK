package com.seoulink.backend.domain.course.controller;

import com.seoulink.backend.domain.course.dto.response.CourseErrorResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
import com.seoulink.backend.domain.course.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 로그인 연동 전 임시 회원 ID를 사용해 내 코스 목록을 조회한다. */
@RestController
@RequestMapping("/api/members/me/courses")
public class MemberCourseController {

    private final CourseService courseService;

    public MemberCourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /** 로그인 통합 후에는 memberId 대신 인증된 회원 ID를 사용한다. */
    @GetMapping
    public List<CourseRecommendationResponse> getMyCourses(
            @RequestParam Long memberId
    ) {
        return courseService.getMemberCourses(memberId);
    }

    /** 회원 ID 형식 오류를 클라이언트 입력 오류인 400으로 반환한다. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CourseErrorResponse handleInvalidRequest(
            IllegalArgumentException exception
    ) {
        return new CourseErrorResponse("INVALID_REQUEST", exception.getMessage());
    }
}
