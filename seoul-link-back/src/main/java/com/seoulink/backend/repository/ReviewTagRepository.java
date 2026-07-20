package com.seoulink.backend.repository;

import com.seoulink.backend.entity.ReviewTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ReviewTagRepository extends JpaRepository<ReviewTag, Long> {
    List<ReviewTag> findByReviewId(Long reviewId);
    void deleteByReviewId(Long reviewId);
    @Query("select t.tagName, count(t) from ReviewTag t group by t.tagName order by count(t) desc")
    List<Object[]> findPopularTags();
}
