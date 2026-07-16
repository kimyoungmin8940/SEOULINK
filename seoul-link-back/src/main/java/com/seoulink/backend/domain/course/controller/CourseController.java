package com.seoulink.backend.domain.course.controller;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CourseErrorResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.service.CourseOptimizationService;
import com.seoulink.backend.domain.course.service.CourseRecommendationService;
import com.seoulink.backend.domain.course.service.CourseSaveService;
import com.seoulink.backend.domain.course.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 여행 코스 최적화·저장·조회 HTTP 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    // 최적화만 실행하는 흐름, 최적화 후 저장하는 흐름, 저장·조회 흐름을 각각 분리한다.
    private final CourseOptimizationService courseOptimizationService;
    private final CourseRecommendationService courseRecommendationService;
    private final CourseSaveService courseSaveService;
    private final CourseService courseService;

    public CourseController(
            CourseOptimizationService courseOptimizationService,
            CourseRecommendationService courseRecommendationService,
            CourseSaveService courseSaveService,
            CourseService courseService
    ) {
        this.courseOptimizationService = courseOptimizationService;
        this.courseRecommendationService = courseRecommendationService;
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

    /** 추천 후보 최적화부터 DB 저장까지 한 번에 처리한다. */
    @PostMapping("/recommend")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseRecommendResponse recommendCourse(
            @RequestBody CourseRecommendRequest request
    ) {
        return courseRecommendationService.recommendAndSave(request);
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

    /** 로그인 연동 전에는 memberId를 임시 쿼리 파라미터로 받는다. */
    @GetMapping("/recommended")
    public List<CourseRecommendationResponse> getRecommendedCourses(
            @RequestParam Long memberId
    ) {
        return courseService.getRecommendedCourses(memberId);
    }

    /**
     * 최적화 입력값 오류를 클라이언트가 구분할 수 있도록 400으로 반환한다.
     * 전역 예외 응답 형식이 정해지면 ApiExceptionHandler로 이동할 수 있다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CourseErrorResponse handleInvalidRequest(
            IllegalArgumentException exception
    ) {
        return new CourseErrorResponse("INVALID_REQUEST", exception.getMessage());
    }

    /** 존재하지 않는 코스를 조회한 경우 404 응답으로 변환한다. */
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public CourseErrorResponse handleCourseNotFound(
            NoSuchElementException exception
    ) {
        return new CourseErrorResponse("COURSE_NOT_FOUND", exception.getMessage());
    }

    /** 저장·최적화 내부 상태 오류는 상세 원인을 노출하지 않고 공통 500 코드로 반환한다. */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CourseErrorResponse handleCourseProcessingFailure(
            IllegalStateException exception
    ) {
        return new CourseErrorResponse(
                "COURSE_PROCESSING_FAILED",
                "코스 처리 중 오류가 발생했습니다."
        );
    }
}
