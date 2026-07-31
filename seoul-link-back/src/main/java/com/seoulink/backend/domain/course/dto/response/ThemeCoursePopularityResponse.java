package com.seoulink.backend.domain.course.dto.response;

/**
 * 메인 화면의 인기 테마 코스 정렬에 사용하는 저장 횟수 응답이다.
 */
public record ThemeCoursePopularityResponse(
        String sourceCourseKey,
        long saveCount
) {
}
