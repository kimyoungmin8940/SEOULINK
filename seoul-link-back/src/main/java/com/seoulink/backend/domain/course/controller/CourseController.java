package com.seoulink.backend.domain.course.controller;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.service.CourseOptimizationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 코스 최적화 HTTP 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseOptimizationService courseOptimizationService;

    public CourseController(CourseOptimizationService courseOptimizationService) {
        this.courseOptimizationService = courseOptimizationService;
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
     * 최적화 입력값 오류를 클라이언트가 구분할 수 있도록 400으로 반환한다.
     * 전역 예외 응답 형식이 정해지면 ApiExceptionHandler로 이동할 수 있다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidRequest(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
