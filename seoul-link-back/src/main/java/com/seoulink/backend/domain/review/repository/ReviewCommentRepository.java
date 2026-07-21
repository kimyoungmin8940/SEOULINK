package com.seoulink.backend.domain.review.repository;

import com.seoulink.backend.domain.review.entity.ReviewComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {
    List<ReviewComment> findByReviewIdAndIsDeletedOrderByCreatedAtAsc(Long reviewId, String isDeleted);
    Long countByReviewIdAndIsDeleted(Long reviewId, String isDeleted);
}
