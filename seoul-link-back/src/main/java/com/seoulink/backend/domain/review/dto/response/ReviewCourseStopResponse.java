package com.seoulink.backend.domain.review.dto.response;

import lombok.Getter;

@Getter
/**
 * 클라이언트에 반환할 데이터를 표현하는 DTO입니다.
 */
public class ReviewCourseStopResponse {
    private final Integer order;
    private final String visitTime;
    private final String placeName;
    private final String memo;

    public ReviewCourseStopResponse(Integer order, String visitTime, String placeName, String memo) {
        this.order = order;
        this.visitTime = visitTime;
        this.placeName = placeName;
        this.memo = memo;
    }
}
