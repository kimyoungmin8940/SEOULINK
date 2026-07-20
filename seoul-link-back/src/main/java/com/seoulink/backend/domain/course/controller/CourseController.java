package com.seoulink.backend.domain.course.controller;

import com.seoulink.backend.domain.course.dto.response.CourseDraftResponse;
import com.seoulink.backend.domain.course.service.CourseDraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 코스 관련 HTTP 요청을 처리하는 컨트롤러이다.
 *
 * <p>추천 코스 목록, 테마별 코스, 코스 상세, 사용자 코스 생성·수정 등의 API를 제공하고
 * 실제 로직은 {@code CourseService}에 위임한다.</p>
 */

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    // TODO: 담당 기능의 요구사항과 API 명세가 확정되면 구현한다.
    private final CourseDraftService courseDraftService;

    public CourseController(
            CourseDraftService courseDraftService
    ) {
        this.courseDraftService = courseDraftService;
    }

    /**
     * 설문 번호를 기준으로 날짜별 추천 코스 초안을 조회합니다.
     * 요청 예시: GET /api/courses/draft?surveyId=1
     */
    @GetMapping("/draft")
    public ResponseEntity<CourseDraftResponse> getCourseDraft(
            @RequestParam Long surveyId
    ) {
        CourseDraftResponse response =
                courseDraftService.createDraft(surveyId);

        return ResponseEntity.ok(response);
    }
}
