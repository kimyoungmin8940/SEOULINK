package com.seoulink.backend.repository;

import com.seoulink.backend.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {
    List<ReviewImage> findByReviewIdOrderByDisplayOrderAsc(Long reviewId);
    void deleteByReviewId(Long reviewId);
}
