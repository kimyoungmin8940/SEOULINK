package com.seoulink.backend.domain.review.repository;

import com.seoulink.backend.domain.review.entity.ReviewComment;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 도메인 데이터를 조회하고 저장하는 리포지토리입니다.
 */
public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {
    List<ReviewComment> findByReviewIdAndIsDeletedOrderByCreatedAtAsc(Long reviewId, String isDeleted);
    List<ReviewComment> findByMemberIdAndIsDeletedOrderByCreatedAtDesc(Long memberId, String isDeleted);
    Long countByReviewIdAndIsDeleted(Long reviewId, String isDeleted);

    @Modifying
    @Query("""
        update ReviewComment comment
        set comment.isDeleted = 'Y', comment.updatedAt = CURRENT_TIMESTAMP
        where comment.reviewId = :reviewId
          and comment.isDeleted = 'N'
    """)
    int softDeleteByReviewId(@Param("reviewId") Long reviewId);
}
