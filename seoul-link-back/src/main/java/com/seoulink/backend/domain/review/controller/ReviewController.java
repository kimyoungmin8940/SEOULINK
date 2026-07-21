package com.seoulink.backend.domain.review.controller;

import com.seoulink.backend.domain.review.dto.request.CommentCreateRequest;
import com.seoulink.backend.domain.review.dto.request.ReviewCreateRequest;
import com.seoulink.backend.domain.review.dto.request.ReviewUpdateRequest;
import com.seoulink.backend.domain.review.dto.response.ReviewResponse;
import com.seoulink.backend.domain.review.entity.ReviewComment;
import com.seoulink.backend.domain.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService service;
    public ReviewController(ReviewService service) { this.service = service; }

    @PostMapping public ReviewResponse create(@Valid @RequestBody ReviewCreateRequest request) { return service.createReview(request); }
    @GetMapping public Page<ReviewResponse> list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="9") int size,
        @RequestParam(defaultValue="date") String sort, @RequestParam(required=false) String keyword, @RequestParam(required=false) Long memberId) { return service.getReviews(page,size,sort,keyword,memberId); }
    @GetMapping("/popular-tags") public List<String> popularTags() { return service.popularTags(); }
    @GetMapping("/places/{placeId}") public Page<ReviewResponse> byPlace(@PathVariable Long placeId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="9") int size, @RequestParam(defaultValue="date") String sort, @RequestParam(required=false) Long memberId) { return service.getPlaceReviews(placeId,page,size,sort,memberId); }
    @GetMapping("/members/{memberId}") public Page<ReviewResponse> byMember(@PathVariable Long memberId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="9") int size, @RequestParam(defaultValue="date") String sort) { return service.getMemberReviews(memberId,page,size,sort); }
    @GetMapping("/{reviewId}") public ReviewResponse detail(@PathVariable Long reviewId, @RequestParam(required=false) Long memberId) { return service.getReview(reviewId,memberId); }
    @PatchMapping("/{reviewId}") public ReviewResponse update(@PathVariable Long reviewId, @Valid @RequestBody ReviewUpdateRequest request) { return service.updateReview(reviewId,request); }
    @DeleteMapping("/{reviewId}") public void delete(@PathVariable Long reviewId,@RequestParam Long memberId) { service.deleteReview(reviewId,memberId); }
    @PostMapping("/{reviewId}/likes") public String like(@PathVariable Long reviewId,@RequestParam Long memberId) { return service.toggleLike(reviewId,memberId); }
    @PostMapping("/{reviewId}/comments") public ReviewComment comment(@PathVariable Long reviewId,@Valid @RequestBody CommentCreateRequest request) { return service.createComment(reviewId,request); }
    @GetMapping("/{reviewId}/comments") public List<ReviewComment> comments(@PathVariable Long reviewId) { return service.getComments(reviewId); }
}
