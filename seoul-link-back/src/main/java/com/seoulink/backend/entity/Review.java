package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "REVIEW")
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REVIEW_ID")
    private Long reviewId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "PLACE_ID", nullable = false)
    private Long placeId;

    @Column(name = "REVIEW_TITLE", nullable = false)
    private String reviewTitle;

    @Lob
    @Column(name = "REVIEW_CONTENT", nullable = false)
    private String reviewContent;

    @Column(name = "RATING", nullable = false)
    private Double rating;

    @Column(name = "IMAGE_URL")
    private String imageUrl;

    @Column(name = "COURSE_ID")
    private Long courseId;

    @Column(name = "VISIT_DATE")
    private LocalDate visitDate;

    @Column(name = "COMPANION")
    private String companion;

    @Column(name = "VIEW_COUNT", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "IS_DELETED", nullable = false)
    private String isDeleted = "N";

    public Review(Long memberId, Long placeId, String reviewTitle, String reviewContent, Double rating, String imageUrl) {
        this.memberId = memberId;
        this.placeId = placeId;
        this.reviewTitle = reviewTitle;
        this.reviewContent = reviewContent;
        this.rating = rating;
        this.imageUrl = imageUrl;
    }

    public void update(String reviewTitle, String reviewContent, Double rating, String imageUrl, LocalDate visitDate, String companion) {
        this.reviewTitle = reviewTitle;
        this.reviewContent = reviewContent;
        this.rating = rating;
        this.imageUrl = imageUrl;
        this.visitDate = visitDate;
        this.companion = companion;
        this.updatedAt = LocalDateTime.now();
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public void deleteReview() {
        this.isDeleted = "Y";
        this.updatedAt = LocalDateTime.now();
    }
}
