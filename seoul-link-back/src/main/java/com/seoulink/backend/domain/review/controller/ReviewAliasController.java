package com.seoulink.backend.domain.review.controller;

import com.seoulink.backend.domain.review.dto.response.ReviewResponse;
import com.seoulink.backend.domain.review.service.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
/**
 * HTTP 요청을 도메인 서비스로 연결하는 컨트롤러입니다.
 */
public class ReviewAliasController {

    private final ReviewService reviewService;

    public ReviewAliasController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/places/{placeId}/reviews")
    public Page<ReviewResponse> getPlaceReviews(
            @PathVariable Long placeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sort
    ) {
        return reviewService.getPlaceReviews(placeId, page, size, sort);
    }

    @GetMapping("/api/members/{memberId}/reviews")
    public Page<ReviewResponse> getMemberReviews(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sort
    ) {
        return reviewService.getMemberReviews(memberId, page, size, sort);
    }
}
