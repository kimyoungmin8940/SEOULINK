package com.seoulink.backend.global.exception;

/**
 * 애플리케이션 전역 예외를 공통 형식으로 처리할 클래스이다.
 *
 * <p>컨트롤러마다 반복해서 예외 응답을 작성하지 않도록
 * {@code @RestControllerAdvice}와 {@code @ExceptionHandler}를 이용해
 * 유효성 검증 오류, 존재하지 않는 데이터, 비즈니스 예외 등을 처리한다.</p>
 *
 * <p>실제 예외 정책과 응답 형식이 확정되면 구현한다.</p>
 */
public class ApiExceptionHandler {
    // TODO: 담당 기능의 요구사항과 API 명세가 확정되면 구현한다.
}
