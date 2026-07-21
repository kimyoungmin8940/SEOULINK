package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 저장된 코스의 장소별 방문 순서와 화면 표시 정보를 반환한다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoursePlaceResponse {

    // COURSE_DETAILS와 PLACES를 연결하는 식별자이다.
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
    private Double recommendationScore;

    // 추천 후보에서 전달된 8개 테마 여부이다.
    private String themePalaceCultureYn;
    private String themeNatureHangangYn;
    private String themeDateYn;
    private String themeFoodTourYn;
    private String themeCafeTourYn;
    private String themeShoppingHotplaceYn;
    private String themeNightViewYn;
    private String themeHotelStayYn;

    // 날짜 정보는 상위 CourseDayResponse가 담당하며, 이 DTO는 하루 안의 순서를 표현한다.
    private Integer visitOrder;
    private String memo;
    private String visitTime;
    private Integer expectedVisitMinutes;

    // 같은 날짜의 첫 장소는 이전 장소가 없으므로 두 값이 0이다.
    private Double distanceFromPreviousKm;
    private Double travelTimeFromPreviousMinutes;
}
