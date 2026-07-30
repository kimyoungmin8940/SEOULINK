package com.seoulink.backend.domain.course.controller;

import com.seoulink.backend.domain.course.dto.request.CourseBatchSaveRequest;
import com.seoulink.backend.domain.course.dto.request.CourseDraftRefreshRequest;
import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseBatchSaveResponse;
import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CourseDraftResponse;
import com.seoulink.backend.domain.course.dto.response.CourseErrorResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.service.CourseDraftService;
import com.seoulink.backend.domain.course.service.CourseOptimizationService;
import com.seoulink.backend.domain.course.service.CourseRecommendationHistoryService;
import com.seoulink.backend.domain.course.service.CourseRecommendationService;
import com.seoulink.backend.domain.course.service.CourseSaveService;
import com.seoulink.backend.domain.course.service.CourseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 * 추천 후보 초안 생성부터 코스 최적화·저장·조회까지의 HTTP 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);

    // 후보 초안, 최적화, 추천 코스 생성, 저장, 조회 책임을 각 서비스에 위임한다.
    private final CourseDraftService courseDraftService;
    private final CourseOptimizationService courseOptimizationService;
    private final CourseRecommendationService courseRecommendationService;
    private final CourseRecommendationHistoryService
            courseRecommendationHistoryService;
    private final CourseSaveService courseSaveService;
    private final CourseService courseService;

    public CourseController(
            CourseDraftService courseDraftService,
            CourseOptimizationService courseOptimizationService,
            CourseRecommendationService courseRecommendationService,
            CourseRecommendationHistoryService
                    courseRecommendationHistoryService,
            CourseSaveService courseSaveService,
            CourseService courseService
    ) {
        this.courseDraftService = courseDraftService;
        this.courseOptimizationService = courseOptimizationService;
        this.courseRecommendationService = courseRecommendationService;
        this.courseRecommendationHistoryService =
                courseRecommendationHistoryService;
        this.courseSaveService = courseSaveService;
        this.courseService = courseService;
    }

    /** 설문 번호를 기준으로 날짜별 추천 장소 후보 초안을 생성한다. */
    @GetMapping("/draft")
    public ResponseEntity<CourseDraftResponse> getCourseDraft(
            @RequestParam Long surveyId
    ) {
        return ResponseEntity.ok(courseDraftService.createDraft(surveyId));
    }

    /**
     * 다시 추천받기에서 직전 결과의 장소를 우선 제외하고 후보 풀을 DB에서 새로 조회한다.
     * 이 단계에서는 외부 경로 API를 호출하지 않는다.
     */
    @PostMapping("/draft/recommend-again")
    public ResponseEntity<CourseDraftResponse> refreshCourseDraft(
            @RequestBody CourseDraftRefreshRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("재추천 후보 요청이 필요합니다.");
        }
        return ResponseEntity.ok(
                courseDraftService.createDraftForRecommendAgain(
                        request.getSurveyId(),
                        request.getPreviouslyRecommendedPlaceIds()
                )
        );
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
     * 추천 카드에 현재 표시되는 DAY의 고정된 방문 순서만 실제 경로로 보완한다.
     * 대중교통에서는 인접한 장소 쌍만 ODsay로 조회한다.
     */
    @PostMapping("/route-details")
    public CourseOptimizeResponse resolveRouteDetails(
            @RequestBody CourseOptimizeRequest request
    ) {
        return courseOptimizationService.resolveFixedRouteDetails(request);
    }

    /** 날짜별 후보 풀에서 서로 다른 추천 코스 3개를 생성해 반환한다. */
    @PostMapping("/recommend")
    public CourseRecommendResponse recommendCourse(
            @RequestBody CourseRecommendRequest request
    ) {
        CourseRecommendResponse response =
                courseRecommendationService.recommend(request);
        courseRecommendationHistoryService.record(request, response);
        return response;
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

    /** 선택한 추천 코스 여러 개를 하나의 트랜잭션으로 저장한다. */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseBatchSaveResponse saveCourses(
            @RequestBody CourseBatchSaveRequest request
    ) {
        return courseSaveService.saveOptimizedCourses(request);
    }

    /** 저장된 코스 기본정보와 날짜별 장소 순서를 조회한다. */
    @GetMapping("/{courseId}")
    public CourseDetailResponse getCourse(
            @PathVariable Long courseId,
            @RequestParam(required = false) Long memberId
    ) {
        // 로그인 통합 전에는 memberId가 전달된 경우에만 비공개 코스 소유권을 확인한다.
        // JWT 인증이 합쳐지면 쿼리 파라미터 대신 인증 객체의 회원 ID를 넘기면 된다.
        return memberId == null
                ? courseService.getCourse(courseId)
                : courseService.getMemberCourse(courseId, memberId);
    }

    /** 로그인 회원이 저장한 모든 코스를 최신순으로 조회한다. */
    @GetMapping("/my")
    public List<CourseRecommendationResponse> getMyCourses(
            @RequestParam Long memberId
    ) {
        return courseService.getMemberCourses(memberId);
    }

    /** 로그인 연동 전에는 memberId를 임시 쿼리 파라미터로 받는다. */
    @GetMapping("/recommended")
    public List<CourseRecommendationResponse> getRecommendedCourses(
            @RequestParam Long memberId
    ) {
        return courseService.getRecommendedCourses(memberId);
    }

    /**
     * 수정 전 브라우저 세션에만 남아 있던 추천 응답을 추천 이력으로 복구한다.
     * 같은 결과·장소 구성은 저장 서비스에서 중복 행 없이 재사용한다.
     */
    @PostMapping("/recommended/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordRecommendedCourseHistory(
            @RequestBody CourseRecommendResponse response
    ) {
        courseRecommendationHistoryService.record(response);
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

    /** enum에 없는 이동수단처럼 JSON 자체를 변환할 수 없는 요청도 400으로 반환한다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CourseErrorResponse handleUnreadableRequest(
            HttpMessageNotReadableException exception
    ) {
        return new CourseErrorResponse(
                "INVALID_REQUEST",
                "요청 JSON의 값 또는 형식이 올바르지 않습니다."
        );
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
        log.error("코스 처리 중 내부 오류가 발생했습니다.", exception);
        return new CourseErrorResponse(
                "COURSE_PROCESSING_FAILED",
                "코스 처리 중 오류가 발생했습니다."
        );
    }
}
