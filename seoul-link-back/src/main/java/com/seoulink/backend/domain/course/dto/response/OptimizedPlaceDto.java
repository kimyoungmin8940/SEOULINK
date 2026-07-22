package com.seoulink.backend.domain.course.dto.response;

import com.seoulink.backend.domain.course.model.TransitPathType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 최적화가 끝난 장소 한 건과 계산된 방문 순서 정보를 표현한다.
 *
 * <p>같은 날짜의 첫 장소는 이전 장소가 없으므로
     * {@code distanceFromPreviousKm}와 {@code travelTimeFromPreviousMinutes}가 0이고,
     * {@code transitPathType}은 null이다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizedPlaceDto {

    // 원본 후보에서 유지되는 장소 표시·추천·좌표·날짜 정보이다.
    private Long placeId;
    private String placeName;
    private String category;
    private String address;
    private String roadAddress;
    private String imageUrl;
    private Double recommendationScore;
    private Double latitude;
    private Double longitude;
    private LocalDate visitDate;

    // 원본 추천 장소의 8개 테마 여부를 최적화 결과에서도 유지한다.
    private String themePalaceCultureYn;
    private String themeNatureHangangYn;
    private String themeDateYn;
    private String themeFoodTourYn;
    private String themeCafeTourYn;
    private String themeShoppingHotplaceYn;
    private String themeNightViewYn;
    private String themeHotelStayYn;

    // 최적화 단계에서 새로 계산되는 체류시간·방문 순서·이동 정보이다.
    private Integer expectedVisitMinutes;
    private Integer visitOrder;
    private Double distanceFromPreviousKm;
    private Double travelTimeFromPreviousMinutes;
    private TransitPathType transitPathType;
}
