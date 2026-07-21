package com.seoulink.backend.domain.review.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "REVIEW_IMAGE")
@Getter
@NoArgsConstructor
public class ReviewImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REVIEW_IMAGE_ID") private Long reviewImageId;
    @Column(name = "REVIEW_ID", nullable = false) private Long reviewId;
    @Column(name = "IMAGE_URL", nullable = false) private String imageUrl;
    @Column(name = "DISPLAY_ORDER", nullable = false) private Integer displayOrder;
    @Column(name = "CREATED_AT", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public ReviewImage(Long reviewId, String imageUrl, Integer displayOrder) {
        this.reviewId = reviewId; this.imageUrl = imageUrl; this.displayOrder = displayOrder;
    }
}
