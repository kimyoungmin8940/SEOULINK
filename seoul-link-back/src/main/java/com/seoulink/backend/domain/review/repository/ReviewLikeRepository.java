package com.seoulink.backend.domain.review.repository;

import com.seoulink.backend.domain.review.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    boolean existsByReviewIdAndMemberId(Long reviewId, Long memberId);
    long countByReviewId(Long reviewId);
    void deleteByReviewIdAndMemberId(Long reviewId, Long memberId);
}