package com.seoulink.backend.domain.review.controller;

import com.seoulink.backend.domain.review.dto.request.CommentCreateRequest;
import com.seoulink.backend.domain.review.dto.request.ReviewCreateRequest;
import com.seoulink.backend.domain.review.dto.request.ReviewUpdateRequest;
import com.seoulink.backend.domain.review.dto.response.ReviewResponse;
import com.seoulink.backend.domain.review.entity.ReviewComment;
import com.seoulink.backend.domain.review.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    /** 새 여행 리뷰와 선택한 코스 정보를 등록한다. */
    @PostMapping
    public ReviewResponse create(@Valid @RequestBody ReviewCreateRequest request) {
        return service.createReview(request);
    }

    /** 검색어, 정렬, 회원별 좋아요 상태를 반영한 리뷰 목록을 조회한다. */
    @GetMapping
    public Page<ReviewResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(defaultValue = "date") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long memberId
    ) {
        return service.getReviews(page, size, sort, keyword, memberId);
    }

    /** 화면의 태그 필터에 사용할 인기 태그를 조회한다. */
    @GetMapping("/popular-tags")
    public List<String> popularTags() {
        return service.popularTags();
    }

    /** 특정 장소에 작성된 리뷰를 페이지 단위로 조회한다. */
    @GetMapping("/places/{placeId}")
    public Page<ReviewResponse> byPlace(
            @PathVariable Long placeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(defaultValue = "date") String sort,
            @RequestParam(required = false) Long memberId
    ) {
        return service.getPlaceReviews(placeId, page, size, sort, memberId);
    }

    /** 특정 회원이 작성한 리뷰를 페이지 단위로 조회한다. */
    @GetMapping("/members/{memberId}")
    public Page<ReviewResponse> byMember(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "9") int size,
            @RequestParam(defaultValue = "date") String sort
    ) {
        return service.getMemberReviews(memberId, page, size, sort);
    }

    /** 리뷰 상세와 선택적 로그인 회원의 좋아요 상태를 조회한다. */
    @GetMapping("/{reviewId}")
    public ReviewResponse detail(
            @PathVariable Long reviewId,
            @RequestParam(required = false) Long memberId
    ) {
        return service.getReview(reviewId, memberId);
    }

    /** 작성자 본인의 리뷰 내용을 수정한다. */
    @PatchMapping("/{reviewId}")
    public ReviewResponse update(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewUpdateRequest request
    ) {
        return service.updateReview(reviewId, request);
    }

    /** 작성자 본인의 리뷰를 논리 삭제한다. */
    @DeleteMapping("/{reviewId}")
    public void delete(
            @PathVariable Long reviewId,
            @RequestParam Long memberId
    ) {
        service.deleteReview(reviewId, memberId);
    }

    /** 로그인 회원 기준으로 좋아요를 추가하거나 해제한다. */
    @PostMapping("/{reviewId}/likes")
    public String like(
            @PathVariable Long reviewId,
            @RequestParam Long memberId
    ) {
        return service.toggleLike(reviewId, memberId);
    }

    /** 리뷰에 댓글을 등록한다. */
    @PostMapping("/{reviewId}/comments")
    public ReviewComment comment(
            @PathVariable Long reviewId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        return service.createComment(reviewId, request);
    }

    /** 리뷰에 등록된 삭제되지 않은 댓글을 조회한다. */
    @GetMapping("/{reviewId}/comments")
    public List<ReviewComment> comments(@PathVariable Long reviewId) {
        return service.getComments(reviewId);
    }
}
