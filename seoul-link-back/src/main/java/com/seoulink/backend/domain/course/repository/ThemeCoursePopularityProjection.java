package com.seoulink.backend.domain.course.repository;

/**
 * 원본 테마 코스별 회원 저장 횟수 집계 결과이다.
 */
public interface ThemeCoursePopularityProjection {

    String getSourceCourseKey();

    long getSaveCount();
}
