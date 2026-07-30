package com.seoulink.backend.domain.course.dto.response;

import com.seoulink.backend.domain.course.model.TransitPathType;
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

    // COURSE_DETAILS에는 중복 저장하지 않고, 상세 조회 시 placeId로 PLACES를 일괄 조회해 채운다.
    private String placeName;
    private String category;
    private String region;
    private String address;
    private String roadAddress;
    private String imageUrl;
    private Double latitude;
    private Double longitude;
    private Double recommendationScore;

    // PLACES에 저장된 장소별 8개 테마 여부이다.
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

    // DAY 1 첫 장소는 거리·시간 0, 경로 종류 null이다. DAY 2 이후 첫 일반
    // 장소는 화면에 별도로 복원되는 전날 숙소에서 이동해 온 경로값을 가질 수 있다.
    private Double distanceFromPreviousKm;
    private Double travelTimeFromPreviousMinutes;
    private TransitPathType transitPathType;
    private Boolean routeEstimated;
}
