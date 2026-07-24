package com.seoulink.backend.domain.review.repository;

import com.seoulink.backend.domain.review.entity.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 도메인 데이터를 조회하고 저장하는 리포지토리입니다.
 */
public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {
    List<ReviewComment> findByReviewIdAndIsDeletedOrderByCreatedAtAsc(Long reviewId, String isDeleted);
    Long countByReviewIdAndIsDeleted(Long reviewId, String isDeleted);
}
