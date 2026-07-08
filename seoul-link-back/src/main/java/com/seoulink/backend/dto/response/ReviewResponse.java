package com.seoulink.backend.dto.response;

import com.seoulink.backend.entity.Review;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ReviewResponse {
    private Long reviewId;
    private Long memberId;
    private Long placeId;
    private String reviewTitle;
    private String reviewContent;
    private Double rating;
    private String imageUrl;
    private Integer viewCount;
    private Long likeCount;
    private LocalDateTime createdAt;

    public ReviewResponse(Review review, Long likeCount) {
        this.reviewId = review.getReviewId();
        this.memberId = review.getMemberId();
        this.placeId = review.getPlaceId();
        this.reviewTitle = review.getReviewTitle();
        this.reviewContent = review.getReviewContent();
        this.rating = review.getRating();
        this.imageUrl = review.getImageUrl();
        this.viewCount = review.getViewCount();
        this.likeCount = likeCount;
        this.createdAt = review.getCreatedAt();
    }
}