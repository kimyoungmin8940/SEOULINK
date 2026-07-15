package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 저장된 코스의 장소별 방문 순서와 화면 표시 정보를 반환한다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoursePlaceResponse {

    private Long detailId;
    private Long placeId;

    /* PLACES 도메인 통합 후 placeId로 조회해 채울 화면 표시 정보이다. */
    private String placeName;
    private String category;
    private String address;
    private String roadAddress;
    private String imageUrl;
    private Double latitude;
    private Double longitude;

    private Integer dayNo;
    private LocalDate visitDate;
    private Integer visitOrder;
    private String memo;
    private String visitTime;
    private Integer expectedVisitMinutes;
    private Double distanceFromPreviousKm;
    private Double travelTimeFromPreviousMinutes;
}
