package com.seoulink.backend.domain.review.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "REVIEW_IMAGE")
@Getter
@NoArgsConstructor
/**
 * 데이터베이스에 저장되는 도메인 엔티티입니다.
 */
public class ReviewImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REVIEW_IMAGE_ID") private Long reviewImageId;
    @Column(name = "REVIEW_ID", nullable = false) private Long reviewId;
    @Column(name = "IMAGE_URL", nullable = false, length = 1000) private String imageUrl;
    @Column(name = "DISPLAY_ORDER", nullable = false) private Integer displayOrder;
    @Column(name = "CREATED_AT", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public ReviewImage(Long reviewId, String imageUrl, Integer displayOrder) {
        this.reviewId = reviewId; this.imageUrl = imageUrl; this.displayOrder = displayOrder;
    }
}
