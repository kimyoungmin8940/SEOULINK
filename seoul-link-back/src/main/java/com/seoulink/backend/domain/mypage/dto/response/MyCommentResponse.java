package com.seoulink.backend.domain.mypage.dto.response;

import com.seoulink.backend.domain.review.entity.Review;
import com.seoulink.backend.domain.review.entity.ReviewComment;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MyCommentResponse {
    private final Long commentId;
    private final Long reviewId;
    private final String reviewTitle;
    private final String placeName;
    private final String content;
    private final LocalDateTime createdAt;

    public MyCommentResponse(ReviewComment comment, Review review, String placeName) {
        this.commentId = comment.getCommentId();
        this.reviewId = review.getReviewId();
        this.reviewTitle = review.getReviewTitle();
        this.placeName = placeName;
        this.content = comment.getContent();
        this.createdAt = comment.getCreatedAt();
    }
}
