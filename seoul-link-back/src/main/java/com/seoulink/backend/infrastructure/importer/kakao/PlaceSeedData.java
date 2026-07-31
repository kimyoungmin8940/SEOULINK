package com.seoulink.backend.infrastructure.importer.kakao;

import java.util.List;

import static com.seoulink.backend.infrastructure.importer.kakao.PlaceTheme.*;

public class PlaceSeedData {

    private PlaceSeedData() {
    }

    public static List<PlaceSeed> seeds() {
        return List.of(
                // 여기 안에 장소를 계속 추가하면 됩니다.
                // PLACE_ID는 자동 생성, CREATED_AT/UPDATED_AT은 SYSDATE로 자동 입력됩니다.
                // API_PROVIDER/API_PLACE_ID/API_CATEGORY/REGION/ADDRESS/ROAD_ADDRESS/LATITUDE/LONGITUDE/PHONE/PLACE_URL은 카카오 API에서 가져옵니다.

                PlaceSeed.builder("종로 양꼬치", "RESTAURANT", List.of(FOOD_TOUR, DATE, INDOOR, RAINY_DAY))
                        .searchKeyword("종로 양꼬치")
                        .rating(0.0)
                        .reviewCount(0)
                        .description("종로에서 양꼬치와 식사를 즐기기 좋은 음식점")
                        .imageUrl(null)
                        .tags(false, false, false, false, false, true, true, true)
                        .sourceType("RECOMMEND")
                        .recommendYn("Y")
                        .approvalStatus("APPROVED")
                        .createdByMemberId(null)
                        .condition(true, true, true)
                        .avgStayMinutes(90)
                        .priceLevel(PriceLevel.MEDIUM)
                        .isActive("Y")
                        .build()








        );
    }
}
