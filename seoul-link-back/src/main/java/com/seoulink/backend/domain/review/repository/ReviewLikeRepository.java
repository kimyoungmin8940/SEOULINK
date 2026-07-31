package com.seoulink.backend.domain.review.repository;

import com.seoulink.backend.domain.review.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 도메인 데이터를 조회하고 저장하는 리포지토리입니다.
 */
public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    boolean existsByReviewIdAndMemberId(Long reviewId, Long memberId);
    long countByReviewId(Long reviewId);
    void deleteByReviewIdAndMemberId(Long reviewId, Long memberId);
}