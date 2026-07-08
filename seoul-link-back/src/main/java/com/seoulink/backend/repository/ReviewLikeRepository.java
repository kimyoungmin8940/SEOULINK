package com.seoulink.backend.repository;

import com.seoulink.backend.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    boolean existsByReviewIdAndMemberId(Long reviewId, Long memberId);
    long countByReviewId(Long reviewId);
    void deleteByReviewIdAndMemberId(Long reviewId, Long memberId);
}