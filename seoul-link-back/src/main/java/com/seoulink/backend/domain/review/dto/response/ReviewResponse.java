package com.seoulink.backend.domain.review.dto.response;

import com.seoulink.backend.domain.review.entity.Review;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ReviewResponse {
    private final Long reviewId; private final Long memberId; private final Long placeId; private final Long courseId;
    private final String reviewTitle; private final String reviewContent; private final Double rating;
    private final Integer viewCount; private final Long likeCount; private final Long commentCount;
    private final LocalDateTime createdAt; private final LocalDate visitDate; private final String companion;
    private final String authorName; private final String placeName; private final String placeImageUrl;
    private final List<String> imageUrls; private final List<String> tags; private final boolean likedByMe;
    private final ReviewCourseSummaryResponse courseSummary;

    public ReviewResponse(Review review, Long likeCount) {
        this(review, likeCount, 0L, null, null, null,
                review.getImageUrl() == null ? List.of() : List.of(review.getImageUrl()), List.of(), false, null);
    }

    public ReviewResponse(Review review, Long likeCount, Long commentCount, String authorName, String placeName,
                          String placeImageUrl, List<String> imageUrls, List<String> tags, boolean likedByMe) {
        this(review, likeCount, commentCount, authorName, placeName, placeImageUrl, imageUrls, tags, likedByMe, null);
    }

    public ReviewResponse(Review review, Long likeCount, Long commentCount, String authorName, String placeName,
                          String placeImageUrl, List<String> imageUrls, List<String> tags, boolean likedByMe,
                          ReviewCourseSummaryResponse courseSummary) {
        this.reviewId = review.getReviewId(); this.memberId = review.getMemberId(); this.placeId = review.getPlaceId(); this.courseId = review.getCourseId();
        this.reviewTitle = review.getReviewTitle(); this.reviewContent = review.getReviewContent(); this.rating = review.getRating();
        this.viewCount = review.getViewCount(); this.likeCount = likeCount; this.commentCount = commentCount; this.createdAt = review.getCreatedAt();
        this.visitDate = review.getVisitDate(); this.companion = review.getCompanion(); this.authorName = authorName; this.placeName = placeName;
        this.placeImageUrl = placeImageUrl; this.imageUrls = imageUrls; this.tags = tags; this.likedByMe = likedByMe;
        this.courseSummary = courseSummary;
    }
}
