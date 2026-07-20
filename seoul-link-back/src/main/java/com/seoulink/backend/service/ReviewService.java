package com.seoulink.backend.service;

import com.seoulink.backend.dto.request.CommentCreateRequest;
import com.seoulink.backend.dto.request.ReviewCreateRequest;
import com.seoulink.backend.dto.request.ReviewUpdateRequest;
import com.seoulink.backend.dto.response.ReviewResponse;
import com.seoulink.backend.entity.*;
import com.seoulink.backend.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.seoulink.backend.dto.response.ReviewCourseSummaryResponse;
import com.seoulink.backend.dto.response.ReviewCourseStopResponse;

@Service
public class ReviewService {
    private final ReviewRepository reviews; private final ReviewCommentRepository comments; private final ReviewLikeRepository likes;
    private final ReviewImageRepository images; private final ReviewTagRepository tags; private final MemberRepository members; private final PlaceRepository places;
    private final TravelCourseRepository courses; private final CourseDetailRepository courseDetails;

    public ReviewService(ReviewRepository reviews, ReviewCommentRepository comments, ReviewLikeRepository likes, ReviewImageRepository images,
                         ReviewTagRepository tags, MemberRepository members, PlaceRepository places, TravelCourseRepository courses, CourseDetailRepository courseDetails) {
        this.reviews = reviews; this.comments = comments; this.likes = likes; this.images = images; this.tags = tags; this.members = members; this.places = places; this.courses = courses; this.courseDetails = courseDetails;
    }

    @Transactional
    public ReviewResponse createReview(ReviewCreateRequest request) {
        requireMember(request.getMemberId());
        Long representativePlaceId = resolveReviewPlaceId(request.getMemberId(), request.getCourseId(), request.getPlaceId());
        requirePlace(representativePlaceId);
        Review review = new Review(request.getMemberId(), representativePlaceId, request.getReviewTitle(), request.getReviewContent(), request.getRating(), firstImage(request.getImageUrls()));
        review.setCourseId(request.getCourseId()); review.setVisitDate(request.getVisitDate()); review.setCompanion(request.getCompanion());
        Review saved = reviews.save(review); replaceImagesAndTags(saved.getReviewId(), request.getImageUrls(), request.getTags()); refreshPlaceStats(saved.getPlaceId());
        return toResponse(saved, request.getMemberId());
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, ReviewUpdateRequest request) {
        Review review = activeReview(reviewId); requireOwner(review, request.getMemberId());
        review.update(request.getReviewTitle(), request.getReviewContent(), request.getRating(), firstImage(request.getImageUrls()), request.getVisitDate(), request.getCompanion());
        replaceImagesAndTags(reviewId, request.getImageUrls(), request.getTags()); refreshPlaceStats(review.getPlaceId());
        return toResponse(review, request.getMemberId());
    }

    public Page<ReviewResponse> getReviews(int page, int size, String sort, String keyword, Long memberId) {
        Pageable pageable = "likes".equals(sort) ? PageRequest.of(page, size) : PageRequest.of(page, size, toSort(sort));
        Page<Review> result = "likes".equals(sort) ? reviews.findActiveOrderByLikeCount(pageable) : reviews.searchActive(normalize(keyword), pageable);
        return result.map(review -> toResponse(review, memberId));
    }
    public Page<ReviewResponse> getPlaceReviews(Long placeId, int page, int size, String sort, Long memberId) { return reviews.findByPlaceIdAndIsDeleted(placeId, "N", PageRequest.of(page,size,toSort(sort))).map(review -> toResponse(review, memberId)); }
    public Page<ReviewResponse> getPlaceReviews(Long placeId, int page, int size, String sort) { return getPlaceReviews(placeId, page, size, sort, null); }
    public Page<ReviewResponse> getMemberReviews(Long memberId, int page, int size, String sort) { return reviews.findByMemberIdAndIsDeleted(memberId,"N",PageRequest.of(page,size,toSort(sort))).map(review -> toResponse(review, memberId)); }

    @Transactional public ReviewResponse getReview(Long reviewId, Long viewerId) { Review review = activeReview(reviewId); review.increaseViewCount(); return toResponse(review, viewerId); }
    public ReviewComment createComment(Long reviewId, CommentCreateRequest request) { requireMember(request.getMemberId()); activeReview(reviewId); return comments.save(new ReviewComment(reviewId, request.getMemberId(), request.getContent())); }
    public List<ReviewComment> getComments(Long reviewId) { activeReview(reviewId); return comments.findByReviewIdAndIsDeletedOrderByCreatedAtAsc(reviewId, "N"); }
    @Transactional public String toggleLike(Long reviewId, Long memberId) { activeReview(reviewId); requireMember(memberId); if (likes.existsByReviewIdAndMemberId(reviewId,memberId)) { likes.deleteByReviewIdAndMemberId(reviewId,memberId); return "unliked"; } likes.save(new ReviewLike(reviewId,memberId)); return "liked"; }
    @Transactional public void deleteReview(Long reviewId, Long memberId) { Review review=activeReview(reviewId); requireOwner(review,memberId); review.deleteReview(); refreshPlaceStats(review.getPlaceId()); }
    public List<String> popularTags() { return tags.findPopularTags().stream().limit(8).map(row -> (String) row[0]).toList(); }

    private ReviewResponse toResponse(Review review, Long viewerId) {
        Member member = members.findById(review.getMemberId()).orElse(null); Place place = places.findById(review.getPlaceId()).orElse(null);
        String name = member == null ? "여행자" : (member.getNickname() == null || member.getNickname().isBlank() ? member.getName() : member.getNickname());
        List<String> imageUrls = images.findByReviewIdOrderByDisplayOrderAsc(review.getReviewId()).stream().map(ReviewImage::getImageUrl).toList();
        if (imageUrls.isEmpty() && review.getImageUrl() != null) imageUrls = List.of(review.getImageUrl());
        List<String> reviewTags = tags.findByReviewId(review.getReviewId()).stream().map(ReviewTag::getTagName).toList();
        return new ReviewResponse(review, likes.countByReviewId(review.getReviewId()), comments.countByReviewIdAndIsDeleted(review.getReviewId(),"N"), name,
                place == null ? "서울 여행지" : place.getName(), place == null ? null : place.getImageUrl(), imageUrls, reviewTags,
                viewerId != null && likes.existsByReviewIdAndMemberId(review.getReviewId(), viewerId), courseSummary(review.getCourseId()));
    }
    private ReviewCourseSummaryResponse courseSummary(Long courseId) {
        if (courseId == null) return null;
        return courses.findById(courseId).map(course -> {
            List<ReviewCourseStopResponse> stops = courseDetails.findByCourseIdOrderByDayNoAscPlaceOrderAsc(courseId).stream()
                    .map(detail -> new ReviewCourseStopResponse(detail.getPlaceOrder(), detail.getVisitTime(),
                            places.findById(detail.getPlaceId()).map(Place::getName).orElse("여행 장소"), detail.getMemo()))
                    .toList();
            return new ReviewCourseSummaryResponse(course.getCourseId(), course.getTitle(), course.getRegion(), stops);
        }).orElse(null);
    }
    private void replaceImagesAndTags(Long reviewId, List<String> imageUrls, List<String> reviewTags) { images.deleteByReviewId(reviewId); tags.deleteByReviewId(reviewId); int n=0; for(String url: safe(imageUrls)) if(url != null && !url.isBlank()) images.save(new ReviewImage(reviewId,url.trim(),n++)); for(String tag: safe(reviewTags)) if(tag != null && !tag.isBlank()) tags.save(new ReviewTag(reviewId,tag.trim().replace("#",""))); }
    private String firstImage(List<String> urls) { return safe(urls).stream().filter(url -> url != null && !url.isBlank()).findFirst().orElse(null); }
    private List<String> safe(List<String> value) { return value == null ? List.of() : value; }
    private void requireMember(Long id) { if (!members.existsById(id)) throw new IllegalArgumentException("Member not found."); }
    private Long resolveReviewPlaceId(Long memberId, Long courseId, Long placeId) {
        if (courseId == null) {
            if (placeId == null) throw new IllegalArgumentException("Select a course or a place.");
            return placeId;
        }
        TravelCourse course = courses.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found."));
        if (!Objects.equals(course.getMemberId(), memberId)) throw new IllegalArgumentException("You can review only your own course.");
        return courseDetails.findByCourseIdOrderByDayNoAscPlaceOrderAsc(courseId).stream()
                .findFirst().map(CourseDetail::getPlaceId)
                .orElseThrow(() -> new IllegalArgumentException("The selected course has no places."));
    }
    private void requirePlace(Long id) { places.findById(id).filter(p -> "Y".equals(p.getIsActive())).orElseThrow(() -> new IllegalArgumentException("Place not found.")); }
    private Review activeReview(Long id) { return reviews.findById(id).filter(r -> "N".equals(r.getIsDeleted())).orElseThrow(() -> new IllegalArgumentException("Review not found.")); }
    private void requireOwner(Review review, Long memberId) { if (!Objects.equals(review.getMemberId(), memberId)) throw new IllegalArgumentException("Only the author can change this review."); }
    private void refreshPlaceStats(Long id) { places.findById(id).ifPresent(p -> p.updateReviewStats(reviews.averageRatingByPlaceId(id), reviews.countByPlaceIdAndIsDeleted(id,"N"))); }
    private Sort toSort(String sort) { if("views".equals(sort)) return Sort.by(Sort.Direction.DESC,"viewCount"); if("rating".equals(sort)) return Sort.by(Sort.Direction.DESC,"rating").and(Sort.by(Sort.Direction.DESC,"createdAt")); return Sort.by(Sort.Direction.DESC,"createdAt"); }
    private String normalize(String keyword) { return keyword == null || keyword.isBlank() ? null : keyword.trim(); }
}
