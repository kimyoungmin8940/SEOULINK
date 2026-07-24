package com.seoulink.backend.domain.place.entity;

/**
 * ERD의 {@code PLACES} 테이블과 매핑될 장소 엔티티이다.
 *
 * <p>장소명, 카테고리, 주소, 위도·경도, 이미지, 평점,
 * 운영 정보, 검색용 태그 등 코스 구성에 필요한 장소 정보를 관리한다.</p>
 *
 * <p>컬럼 수가 많으므로 ERD의 컬럼명·자료형·NULL 허용 여부를 기준으로 정확히 매핑한다.</p>
 */

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "PLACES",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_PLACES_API",
                        columnNames = {"API_PROVIDER", "API_PLACE_ID"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PLACE_ID")
    private Long placeId;

    @Column(name = "API_PROVIDER", nullable = false, length = 30)
    private String apiProvider;

    @Column(name = "API_PLACE_ID", nullable = false, length = 100)
    private String apiPlaceId;

    @Column(name = "CONTENT_ID")
    private Long contentId;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "CATEGORY", nullable = false, length = 50)
    private String category;

    @Column(name = "API_CATEGORY", length = 200)
    private String apiCategory;

    @Column(name = "REGION", nullable = false, length = 100)
    private String region;

    @Column(name = "ADDRESS", nullable = false, length = 500)
    private String address;

    @Column(name = "ROAD_ADDRESS", length = 500)
    private String roadAddress;

    @Column(name = "LATITUDE", nullable = false)
    private Double latitude;

    @Column(name = "LONGITUDE", nullable = false)
    private Double longitude;

    @Column(name = "PHONE", length = 50)
    private String phone;

    @Column(name = "PLACE_URL", length = 1000)
    private String placeUrl;

    @Column(name = "RATING", nullable = false)
    private Double rating = 0.0;

    @Column(name = "REVIEW_COUNT", nullable = false)
    private Integer reviewCount = 0;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "IMAGE_URL", length = 1000)
    private String imageUrl;

    @Column(name = "TAG_HISTORY", nullable = false, length = 1)
    private String tagHistory = "N";

    @Column(name = "TAG_MODERN", nullable = false, length = 1)
    private String tagModern = "N";

    @Column(name = "TAG_BUDGET", nullable = false, length = 1)
    private String tagBudget = "N";

    @Column(name = "TAG_LUXURY", nullable = false, length = 1)
    private String tagLuxury = "N";

    @Column(name = "TAG_STABLE", nullable = false, length = 1)
    private String tagStable = "N";

    @Column(name = "TAG_DOPAMINE", nullable = false, length = 1)
    private String tagDopamine = "N";

    @Column(name = "TAG_RELAX", nullable = false, length = 1)
    private String tagRelax = "N";

    @Column(name = "TAG_PACKED", nullable = false, length = 1)
    private String tagPacked = "N";

    @Column(name = "THEME_PALACE_CULTURE_YN", nullable = false, length = 1)
    private String themePalaceCultureYn = "N";

    @Column(name = "THEME_NATURE_HANGANG_YN", nullable = false, length = 1)
    private String themeNatureHangangYn = "N";

    @Column(name = "THEME_DATE_YN", nullable = false, length = 1)
    private String themeDateYn = "N";

    @Column(name = "THEME_FOOD_TOUR_YN", nullable = false, length = 1)
    private String themeFoodTourYn = "N";

    @Column(name = "THEME_CAFE_TOUR_YN", nullable = false, length = 1)
    private String themeCafeTourYn = "N";

    @Column(name = "THEME_SHOPPING_HOTPLACE_YN", nullable = false, length = 1)
    private String themeShoppingHotplaceYn = "N";

    @Column(name = "THEME_NIGHT_VIEW_YN", nullable = false, length = 1)
    private String themeNightViewYn = "N";

    @Column(name = "THEME_HOTEL_STAY_YN", nullable = false, length = 1)
    private String themeHotelStayYn = "N";

    @Column(name = "IS_ACTIVE", nullable = false, length = 1)
    private String isActive = "Y";

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "AVG_STAY_MINUTES")
    private Integer avgStayMinutes = 60;

    @PrePersist
    public void prePersist() {
        if (rating == null) rating = 0.0;
        if (reviewCount == null) reviewCount = 0;
        if (tagHistory == null) tagHistory = "N";
        if (tagModern == null) tagModern = "N";
        if (tagBudget == null) tagBudget = "N";
        if (tagLuxury == null) tagLuxury = "N";
        if (tagStable == null) tagStable = "N";
        if (tagDopamine == null) tagDopamine = "N";
        if (tagRelax == null) tagRelax = "N";
        if (tagPacked == null) tagPacked = "N";
        if (themePalaceCultureYn == null) themePalaceCultureYn = "N";
        if (themeNatureHangangYn == null) themeNatureHangangYn = "N";
        if (themeDateYn == null) themeDateYn = "N";
        if (themeFoodTourYn == null) themeFoodTourYn = "N";
        if (themeCafeTourYn == null) themeCafeTourYn = "N";
        if (themeShoppingHotplaceYn == null) themeShoppingHotplaceYn = "N";
        if (themeNightViewYn == null) themeNightViewYn = "N";
        if (themeHotelStayYn == null) themeHotelStayYn = "N";
        if (isActive == null) isActive = "Y";
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (avgStayMinutes == null) avgStayMinutes = 60;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
