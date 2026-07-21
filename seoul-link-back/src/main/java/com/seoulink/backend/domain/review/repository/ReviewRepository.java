package com.seoulink.backend.domain.review.repository;

import com.seoulink.backend.domain.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByIsDeletedOrderByCreatedAtDesc(String isDeleted);
    List<Review> findByIsDeletedOrderByViewCountDesc(String isDeleted);
    Page<Review> findByPlaceIdAndIsDeleted(Long placeId, String isDeleted, Pageable pageable);
    Page<Review> findByMemberIdAndIsDeleted(Long memberId, String isDeleted, Pageable pageable);
    Integer countByPlaceIdAndIsDeleted(Long placeId, String isDeleted);

    @Query("""
        select coalesce(avg(r.rating), 0)
        from Review r
        where r.placeId = :placeId
          and r.isDeleted = 'N'
    """)
    Double averageRatingByPlaceId(@Param("placeId") Long placeId);

    @Query("""
        select r
        from Review r
        where r.isDeleted = 'N'
          and (:keyword is null
               or lower(r.reviewTitle) like lower(concat('%', :keyword, '%'))
               or r.reviewContent like concat('%', :keyword, '%'))
    """)
    Page<Review> searchActive(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
        select r
        from Review r
        where r.isDeleted = 'N'
        order by (
            select count(rl)
            from ReviewLike rl
            where rl.reviewId = r.reviewId
        ) desc, r.createdAt desc
    """)
    Page<Review> findActiveOrderByLikeCount(Pageable pageable);
}
