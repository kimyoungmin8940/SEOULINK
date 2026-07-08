package com.seoulink.backend.controller;

import com.seoulink.backend.dto.request.CommentCreateRequest;
import com.seoulink.backend.dto.request.ReviewCreateRequest;
import com.seoulink.backend.dto.response.ReviewResponse;
import com.seoulink.backend.entity.Review;
import com.seoulink.backend.entity.ReviewComment;
import com.seoulink.backend.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public Review createReview(@Valid @RequestBody ReviewCreateRequest request) {
        return reviewService.createReview(request);
    }

    @GetMapping
    public Page<ReviewResponse> getReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sort,
            @RequestParam(required = false) String keyword
    ) {
        return reviewService.getReviews(page, size, sort, keyword);
    }

    @GetMapping("/places/{placeId}")
    public Page<ReviewResponse> getPlaceReviews(
            @PathVariable Long placeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sort
    ) {
        return reviewService.getPlaceReviews(placeId, page, size, sort);
    }

    @GetMapping("/members/{memberId}")
    public Page<ReviewResponse> getMemberReviews(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sort
    ) {
        return reviewService.getMemberReviews(memberId, page, size, sort);
    }

    @GetMapping("/{reviewId}")
    public ReviewResponse getReview(@PathVariable Long reviewId) {
        return reviewService.getReview(reviewId);
    }

    @PostMapping("/{reviewId}/comments")
    public ReviewComment createComment(@PathVariable Long reviewId, @Valid @RequestBody CommentCreateRequest request) {
        return reviewService.createComment(reviewId, request);
    }

    @GetMapping("/{reviewId}/comments")
    public List<ReviewComment> getComments(@PathVariable Long reviewId) {
        return reviewService.getComments(reviewId);
    }

    @PostMapping("/{reviewId}/likes")
    public String toggleLike(@PathVariable Long reviewId, @RequestParam Long memberId) {
        return reviewService.toggleLike(reviewId, memberId);
    }

    @DeleteMapping("/{reviewId}")
    public void deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
    }
}
