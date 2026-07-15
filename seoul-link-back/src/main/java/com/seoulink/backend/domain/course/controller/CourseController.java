package com.seoulink.backend.domain.course.controller;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.service.CourseOptimizationService;
import com.seoulink.backend.domain.course.service.CourseSaveService;
import com.seoulink.backend.domain.course.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * 여행 코스 최적화·저장·조회 HTTP 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseOptimizationService courseOptimizationService;
    private final CourseSaveService courseSaveService;
    private final CourseService courseService;

    public CourseController(
            CourseOptimizationService courseOptimizationService,
            CourseSaveService courseSaveService,
            CourseService courseService
    ) {
        this.courseOptimizationService = courseOptimizationService;
        this.courseSaveService = courseSaveService;
        this.courseService = courseService;
    }

    /**
     * 추천 장소 후보를 받아 날짜별 방문 순서를 최적화한다.
     *
     * @param request 추천 장소 후보 목록
     * @return 최적화된 방문 순서와 이동거리·이동시간
     */
    @PostMapping("/optimize")
    public CourseOptimizeResponse optimizeCourse(
            @RequestBody CourseOptimizeRequest request
    ) {
        return courseOptimizationService.optimize(request);
    }

    /**
     * 사용자가 확정한 최적화 코스와 장소 순서를 저장한다.
     * 인증 연동 후에는 요청의 memberId 대신 로그인 사용자 정보를 사용한다.
     *
     * @param request 저장할 코스와 장소별 방문 정보
     * @return 저장된 코스 식별자와 합계 정보
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseSaveResponse saveCourse(@RequestBody CourseSaveRequest request) {
        return courseSaveService.saveOptimizedCourse(request);
    }

    /** 저장된 코스 기본정보와 날짜별 장소 순서를 조회한다. */
    @GetMapping("/{courseId}")
    public CourseDetailResponse getCourse(@PathVariable Long courseId) {
        return courseService.getCourse(courseId);
    }

    /**
     * 최적화 입력값 오류를 클라이언트가 구분할 수 있도록 400으로 반환한다.
     * 전역 예외 응답 형식이 정해지면 ApiExceptionHandler로 이동할 수 있다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidRequest(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleCourseNotFound(NoSuchElementException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
