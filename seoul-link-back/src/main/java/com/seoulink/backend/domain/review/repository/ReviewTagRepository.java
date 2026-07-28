package com.seoulink.backend.domain.review.repository;

import com.seoulink.backend.domain.review.entity.ReviewTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/**
 * 도메인 데이터를 조회하고 저장하는 리포지토리입니다.
 */
public interface ReviewTagRepository extends JpaRepository<ReviewTag, Long> {
    List<ReviewTag> findByReviewId(Long reviewId);
    void deleteByReviewId(Long reviewId);
    @Query("""
        select t.tagName, count(t)
        from ReviewTag t, Review r
        where r.reviewId = t.reviewId
          and r.isDeleted = 'N'
        group by t.tagName
        order by count(t) desc
    """)
    List<Object[]> findPopularTags();
}
