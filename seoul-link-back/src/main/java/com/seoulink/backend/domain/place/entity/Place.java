package com.seoulink.backend.domain.place.entity;

import jakarta.persistence.*;
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
    private String category; // TOUR, RESTAURANT, CAFE, HOTEL

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

    @Column(name = "IS_ACTIVE", nullable = false, length = 1)
    private String isActive = "Y";

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

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
        if (isActive == null) isActive = "Y";
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateReviewStats(Double rating, Integer reviewCount) {
        this.rating = rating == null ? 0.0 : rating;
        this.reviewCount = reviewCount == null ? 0 : reviewCount;
    }
}
