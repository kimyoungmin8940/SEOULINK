package com.seoulink.backend.global.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 애플리케이션 전역 예외를 공통 형식으로 처리할 클래스이다.
 *
 * <p>컨트롤러마다 반복해서 예외 응답을 작성하지 않도록
 * {@code @RestControllerAdvice}와 {@code @ExceptionHandler}를 이용해
 * 유효성 검증 오류, 존재하지 않는 데이터, 비즈니스 예외 등을 처리한다.</p>
 *
 * <p>실제 예외 정책과 응답 형식이 확정되면 구현한다.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * @Valid로 검사한 요청 본문에 오류가 있을 때 처리한다.
     *
     * 예: 날짜 누락, 지역 누락, 답변 목록 누락
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>>
    handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> validationErrors =
                new LinkedHashMap<>();

        for (FieldError fieldError :
                exception.getBindingResult()
                        .getFieldErrors()) {

            validationErrors.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }

        Map<String, Object> response =
                createErrorResponse(
                        HttpStatus.BAD_REQUEST,
                        "입력값을 확인해주세요."
                );

        response.put("validationErrors", validationErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    //@PathVariable, @RequestParam 검증 오류 처리
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>>
    handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        Map<String, Object> response =
                createErrorResponse(
                        HttpStatus.BAD_REQUEST,
                        exception.getMessage()
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    //사용자가 잘못된 값을 전달한 경우 처리
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>>
    handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        Map<String, Object> response =
                createErrorResponse(
                        HttpStatus.BAD_REQUEST,
                        exception.getMessage()
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    //서버의 현재 데이터 상태 때문에 작업할 수 없는 경우 처리
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>>
    handleIllegalState(
            IllegalStateException exception
    ) {
        Map<String, Object> response =
                createErrorResponse(
                        HttpStatus.CONFLICT,
                        exception.getMessage()
                );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    //별도로 처리하지 않은 오류를 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>>
    handleException(
            Exception exception
    ) {
        exception.printStackTrace();

        Map<String, Object> response =
                createErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "서버에서 오류가 발생했습니다."
                );

        return ResponseEntity
                .status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
                .body(response);
    }

    //공통 오류 응답을 생성
    private Map<String, Object> createErrorResponse(
            HttpStatus status,
            String message
    ) {
        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("success", false);
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("message", message);
        response.put(
                "timestamp",
                LocalDateTime.now()
        );

        return response;
    }
}
