package com.seoulink.backend.domain.course.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 코스 최적화의 입력으로 전달되는 장소 후보 한 건을 표현한다.
 *
 * <p>화면 표시 정보, 추천 점수, 좌표, 8개 테마 여부와 함께 특정 장소를 교체할 때만
 * 사용할 대체 후보를 포함한다. {@code visitDate}는 최종 JSON에서는 날짜 그룹의 값을
 * 서비스가 자동으로 채우며, 최적화 내부 DTO 호환을 위해 필드 자체는 유지한다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceCandidateDto {

    // 장소 DB의 식별자와 화면·체류시간 계산에 필요한 기본 정보이다.
    private Long placeId;
    private String placeName;
    private String category;
    private String region;
    private String address;
    private String roadAddress;
    private String imageUrl;

    // 추천 담당자가 계산한 점수로 첫 장소와 경로 비용 동점 후보를 결정한다.
    private Double recommendationScore;

    // 거리 계산 좌표와 최적화 내부에서 사용하는 방문 날짜이다.
    private Double latitude;
    private Double longitude;
    private LocalDate visitDate;

    // 한 장소가 여러 테마에 동시에 해당할 수 있으므로 각각 Y/N으로 전달한다.
    @Builder.Default
    private String themePalaceCultureYn = "N";
    @Builder.Default
    private String themeNatureHangangYn = "N";
    @Builder.Default
    private String themeDateYn = "N";
    @Builder.Default
    private String themeFoodTourYn = "N";
    @Builder.Default
    private String themeCafeTourYn = "N";
    @Builder.Default
    private String themeShoppingHotplaceYn = "N";
    @Builder.Default
    private String themeNightViewYn = "N";
    @Builder.Default
    private String themeHotelStayYn = "N";

    // 이 원본 장소가 먼 구간으로 판정됐을 때만 검토할 전용 대체 후보이다.
    @Builder.Default
    private List<PlaceCandidateDto> alternativeCandidates = new ArrayList<>();
}
