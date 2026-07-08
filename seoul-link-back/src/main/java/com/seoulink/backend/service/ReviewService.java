package com.seoulink.backend.service;

import com.seoulink.backend.dto.request.CommentCreateRequest;
import com.seoulink.backend.dto.request.ReviewCreateRequest;
import com.seoulink.backend.dto.response.ReviewResponse;
import com.seoulink.backend.entity.Review;
import com.seoulink.backend.entity.ReviewComment;
import com.seoulink.backend.entity.ReviewLike;
import com.seoulink.backend.repository.MemberRepository;
import com.seoulink.backend.repository.PlaceRepository;
import com.seoulink.backend.repository.ReviewCommentRepository;
import com.seoulink.backend.repository.ReviewLikeRepository;
import com.seoulink.backend.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository commentRepository;
    private final ReviewLikeRepository likeRepository;
    private final MemberRepository memberRepository;
    private final PlaceRepository placeRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            ReviewCommentRepository commentRepository,
            ReviewLikeRepository likeRepository,
            MemberRepository memberRepository,
            PlaceRepository placeRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.memberRepository = memberRepository;
        this.placeRepository = placeRepository;
    }

    @Transactional
    public Review createReview(ReviewCreateRequest request) {
        validateMemberExists(request.getMemberId());
        validateActivePlaceExists(request.getPlaceId());

        Review review = new Review(
                request.getMemberId(),
                request.getPlaceId(),
                request.getReviewTitle(),
                request.getReviewContent(),
                request.getRating(),
                request.getImageUrl()
        );

        Review savedReview = reviewRepository.save(review);
        refreshPlaceReviewStats(request.getPlaceId());
        return savedReview;
    }

    public Page<ReviewResponse> getReviews(int page, int size, String sort, String keyword) {
        Page<Review> reviews = "likes".equals(sort)
                ? reviewRepository.findActiveOrderByLikeCount(PageRequest.of(page, size))
                : reviewRepository.searchActive(normalizeKeyword(keyword), PageRequest.of(page, size, toSort(sort)));

        return reviews.map(review -> new ReviewResponse(review, likeRepository.countByReviewId(review.getReviewId())));
    }

    public Page<ReviewResponse> getPlaceReviews(Long placeId, int page, int size, String sort) {
        return reviewRepository.findByPlaceIdAndIsDeleted(placeId, "N", PageRequest.of(page, size, toSort(sort)))
                .map(review -> new ReviewResponse(review, likeRepository.countByReviewId(review.getReviewId())));
    }

    public Page<ReviewResponse> getMemberReviews(Long memberId, int page, int size, String sort) {
        return reviewRepository.findByMemberIdAndIsDeleted(memberId, "N", PageRequest.of(page, size, toSort(sort)))
                .map(review -> new ReviewResponse(review, likeRepository.countByReviewId(review.getReviewId())));
    }

    @Transactional
    public ReviewResponse getReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        review.increaseViewCount();

        return new ReviewResponse(review, likeRepository.countByReviewId(reviewId));
    }

    public ReviewComment createComment(Long reviewId, CommentCreateRequest request) {
        validateMemberExists(request.getMemberId());
        validateActiveReviewExists(reviewId);

        ReviewComment comment = new ReviewComment(reviewId, request.getMemberId(), request.getContent());
        return commentRepository.save(comment);
    }

    public List<ReviewComment> getComments(Long reviewId) {
        return commentRepository.findByReviewIdAndIsDeletedOrderByCreatedAtAsc(reviewId, "N");
    }

    @Transactional
    public String toggleLike(Long reviewId, Long memberId) {
        validateActiveReviewExists(reviewId);
        validateMemberExists(memberId);

        boolean liked = likeRepository.existsByReviewIdAndMemberId(reviewId, memberId);

        if (liked) {
            likeRepository.deleteByReviewIdAndMemberId(reviewId, memberId);
            return "좋아요 취소";
        }

        likeRepository.save(new ReviewLike(reviewId, memberId));
        return "좋아요 등록";
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        review.deleteReview();
        refreshPlaceReviewStats(review.getPlaceId());
    }

    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
    }

    private void validateActivePlaceExists(Long placeId) {
        placeRepository.findById(placeId)
                .filter(place -> "Y".equals(place.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("장소를 찾을 수 없습니다."));
    }

    private Review validateActiveReviewExists(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .filter(review -> "N".equals(review.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));
    }

    private void refreshPlaceReviewStats(Long placeId) {
        placeRepository.findById(placeId).ifPresent(place -> place.updateReviewStats(
                reviewRepository.averageRatingByPlaceId(placeId),
                reviewRepository.countByPlaceIdAndIsDeleted(placeId, "N")
        ));
    }

    private Sort toSort(String sort) {
        if ("views".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "viewCount");
        }
        if ("rating".equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}