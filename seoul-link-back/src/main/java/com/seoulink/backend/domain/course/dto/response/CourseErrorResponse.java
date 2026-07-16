package com.seoulink.backend.domain.course.dto.response;

/** 코스 API의 클라이언트 분기용 오류 코드와 사용자 표시용 메시지이다. */
public record CourseErrorResponse(String code, String message) {
}
