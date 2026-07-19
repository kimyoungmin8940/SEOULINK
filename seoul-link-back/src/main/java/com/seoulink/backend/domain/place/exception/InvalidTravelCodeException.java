package com.seoulink.backend.domain.place.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 여행 유형 코드가 팀에서 정한 5자리 규칙에 맞지 않을 때 반환하는 요청 오류이다. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTravelCodeException extends RuntimeException {

    public InvalidTravelCodeException(String message) {
        super(message);
    }
}
