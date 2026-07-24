package com.seoulink.backend.domain.review.service;
import com.seoulink.backend.domain.review.dto.request.CommentCreateRequest;
import com.seoulink.backend.domain.review.dto.request.ReviewCreateRequest;
import com.seoulink.backend.domain.review.dto.request.ReviewUpdateRequest;
import com.seoulink.backend.domain.review.dto.response.ReviewResponse;
import com.seoulink.backend.domain.course.entity.CourseDetail;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import com.seoulink.backend.domain.member.entity.Member;
import com.seoulink.backend.domain.member.repository.MemberRepository;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import com.seoulink.backend.domain.review.entity.Review;
import com.seoulink.backend.domain.review.entity.ReviewComment;
import com.seoulink.backend.domain.review.entity.ReviewImage;
import com.seoulink.backend.domain.review.entity.ReviewLike;
import com.seoulink.backend.domain.review.entity.ReviewTag;
import com.seoulink.backend.domain.review.repository.ReviewCommentRepository;
import com.seoulink.backend.domain.review.repository.ReviewImageRepository;
import com.seoulink.backend.domain.review.repository.ReviewLikeRepository;
import com.seoulink.backend.domain.review.repository.ReviewRepository;
import com.seoulink.backend.domain.review.repository.ReviewTagRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.seoulink.backend.domain.review.dto.response.ReviewCourseSummaryResponse;
import com.seoulink.backend.domain.review.dto.response.ReviewCourseStopResponse;
@Service
/**
 * 도메인 규칙과 트랜잭션을 처리하는 서비스입니다.
 */
public class ReviewService {
    private final ReviewRepository reviews;
    private final ReviewCommentRepository comments;
    private final ReviewLikeRepository likes;
    private final ReviewImageRepository images;
    private final ReviewTagRepository tags;
    private final MemberRepository members;
    private final PlaceRepository places;
    private final TravelCourseRepository courses;
    private final CourseDetailRepository courseDetails;
    public ReviewService(ReviewRepository reviews, ReviewCommentRepository comments, ReviewLikeRepository likes, ReviewImageRepository images, ReviewTagRepository tags, MemberRepository members, PlaceRepository places, TravelCourseRepository courses, CourseDetailRepository courseDetails) {
        this.reviews = reviews;
        this.comments = comments;
        this.likes = likes;
        this.images = images;
        this.tags = tags;
        this.members = members;
        this.places = places;
        this.courses = courses;
        this.courseDetails = courseDetails;
    }
    // 코스 또는 장소의 소유·존재 여부를 검증한 뒤 리뷰, 이미지, 태그를 함께 저장한다.
    @Transactional
    public ReviewResponse createReview(ReviewCreateRequest request) {
        requireMember(request.getMemberId());
        Long representativePlaceId = resolveReviewPlaceId(request.getMemberId(), request.getCourseId(), request.getPlaceId());
        requirePlace(representativePlaceId);
        Review review = new Review(request.getMemberId(), representativePlaceId, request.getReviewTitle(), request.getReviewContent(), request.getRating(), firstImage(request.getImageUrls()));
        review.setCourseId(request.getCourseId());
        review.setVisitDate(request.getVisitDate());
        review.setCompanion(request.getCompanion());
        Review saved = reviews.save(review);
        replaceImagesAndTags(saved.getReviewId(), request.getImageUrls(), request.getTags());
        refreshPlaceStats(saved.getPlaceId());
        return toResponse(saved, request.getMemberId());
    }
    @Transactional
    // 작성자 권한을 확인한 뒤 본문·평점·이미지·태그를 한 트랜잭션에서 교체한다.
    public ReviewResponse updateReview(Long reviewId, ReviewUpdateRequest request) {
        Review review = activeReview(reviewId);
        requireOwner(review, request.getMemberId());
        review.update(request.getReviewTitle(), request.getReviewContent(), request.getRating(), firstImage(request.getImageUrls()), request.getVisitDate(), request.getCompanion());
        replaceImagesAndTags(reviewId, request.getImageUrls(), request.getTags());
        refreshPlaceStats(review.getPlaceId());
        return toResponse(review, request.getMemberId());
    }
    // 좋아요 순과 일반 정렬을 구분해 목록을 조회하고, 화면용 응답으로 변환한다.
    public Page<ReviewResponse> getReviews(int page, int size, String sort, String keyword, Long memberId) {
        Pageable pageable = "likes".equals(sort) ? PageRequest.of(page, size) : PageRequest.of(page, size, toSort(sort));
        Page<Review> result = "likes".equals(sort) ? reviews.findActiveOrderByLikeCount(pageable) : reviews.searchActive(normalize(keyword), pageable);
        return result.map(review -> toResponse(review, memberId));
    }
    // 장소별 리뷰를 정렬 조건과 현재 로그인 회원의 좋아요 상태를 반영해 조회한다.
    public Page<ReviewResponse> getPlaceReviews(Long placeId, int page, int size, String sort, Long memberId) {
        return reviews.findByPlaceIdAndIsDeleted(placeId, "N", PageRequest.of(page,size,toSort(sort))).map(review -> toResponse(review, memberId));
    }
    // 장소별 리뷰를 정렬 조건과 현재 로그인 회원의 좋아요 상태를 반영해 조회한다.
    public Page<ReviewResponse> getPlaceReviews(Long placeId, int page, int size, String sort) {
        return getPlaceReviews(placeId, page, size, sort, null);
    }
    // 특정 회원이 작성한 삭제되지 않은 리뷰만 페이지 단위로 반환한다.
    public Page<ReviewResponse> getMemberReviews(Long memberId, int page, int size, String sort) {
        return reviews.findByMemberIdAndIsDeleted(memberId,"N",PageRequest.of(page,size,toSort(sort))).map(review -> toResponse(review, memberId));
    }
    @Transactional
    // 상세 조회 시 조회 수를 증가시키고, 화면에 필요한 조합 응답을 만든다.
    public ReviewResponse getReview(Long reviewId, Long viewerId) {
        Review review = activeReview(reviewId);
        review.increaseViewCount();
        return toResponse(review, viewerId);
    }
    // 회원과 리뷰 존재 여부를 확인한 뒤 댓글을 저장한다.
    public ReviewComment createComment(Long reviewId, CommentCreateRequest request) {
        requireMember(request.getMemberId());
        activeReview(reviewId);
        return comments.save(new ReviewComment(reviewId, request.getMemberId(), request.getContent()));
    }
    // 삭제되지 않은 댓글을 작성 시각 순서대로 조회한다.
    public List<ReviewComment> getComments(Long reviewId) {
        activeReview(reviewId);
        return comments.findByReviewIdAndIsDeletedOrderByCreatedAtAsc(reviewId, "N");
    }
    @Transactional
    // 이미 좋아요한 회원이면 해제하고, 그렇지 않으면 새 좋아요를 저장한다.
    public String toggleLike(Long reviewId, Long memberId) {
        activeReview(reviewId);
        requireMember(memberId);
        if (likes.existsByReviewIdAndMemberId(reviewId,memberId)) {
            likes.deleteByReviewIdAndMemberId(reviewId,memberId);
            return "unliked";
        }
        likes.save(new ReviewLike(reviewId,memberId));
        return "liked";
    }
    @Transactional
    // 실제 행을 제거하지 않고 논리 삭제한 뒤 장소의 리뷰 통계를 다시 계산한다.
    public void deleteReview(Long reviewId, Long memberId) {
        Review review=activeReview(reviewId);
        requireOwner(review,memberId);
        review.deleteReview();
        refreshPlaceStats(review.getPlaceId());
    }
    // 태그 집계 결과에서 화면에 노출할 상위 8개 태그만 선택한다.
    public List<String> popularTags() {
        return tags.findPopularTags().stream().limit(8).map(row -> (String) row[0]).toList();
    }
    // 엔티티와 이미지·태그·좋아요·코스 정보를 합쳐 화면용 응답을 구성한다.
    private ReviewResponse toResponse(Review review, Long viewerId) {
        Member member = members.findById(review.getMemberId()).orElse(null);
        Place place = places.findById(review.getPlaceId()).orElse(null);
        String name = member == null ? "여행자" : (member.getNickname() == null || member.getNickname().isBlank() ? member.getName() : member.getNickname());
        List<String> imageUrls = images.findByReviewIdOrderByDisplayOrderAsc(review.getReviewId()).stream().map(ReviewImage::getImageUrl).toList();
        if (imageUrls.isEmpty() && review.getImageUrl() != null) imageUrls = List.of(review.getImageUrl());
        List<String> reviewTags = tags.findByReviewId(review.getReviewId()).stream().map(ReviewTag::getTagName).toList();
        return new ReviewResponse(review, likes.countByReviewId(review.getReviewId()), comments.countByReviewIdAndIsDeleted(review.getReviewId(),"N"), name, place == null ? "서울 여행지" : place.getName(), place == null ? null : place.getImageUrl(), imageUrls, reviewTags, viewerId != null && likes.existsByReviewIdAndMemberId(review.getReviewId(), viewerId), courseSummary(review.getCourseId()));
    }
    // 리뷰에 연결된 코스가 있을 때만 방문 순서와 장소 정보를 요약한다.
    private ReviewCourseSummaryResponse courseSummary(Long courseId) {
        if (courseId == null) return null;
        return courses.findById(courseId).map(course -> {
            List<ReviewCourseStopResponse> stops = courseDetails.findByCourseIdOrderByDayNoAscPlaceOrderAsc(courseId).stream() .map(detail -> new ReviewCourseStopResponse(detail.getPlaceOrder(), detail.getVisitTime(), places.findById(detail.getPlaceId()).map(Place::getName).orElse("여행 장소"), detail.getMemo())) .toList();
            return new ReviewCourseSummaryResponse(course.getCourseId(), course.getTitle(), course.getRegion(), stops);
        }
        ).orElse(null);
    }
    // 수정 시 기존 이미지·태그를 교체해 리뷰와 연결된 부가 정보를 동기화한다.
    private void replaceImagesAndTags(Long reviewId, List<String> imageUrls, List<String> reviewTags) {
        images.deleteByReviewId(reviewId);
        tags.deleteByReviewId(reviewId);
        int displayOrder = 1;
        for(String url: safe(imageUrls)) if(url != null && !url.isBlank()) images.save(new ReviewImage(reviewId, url.trim(), displayOrder++));
        for(String tag: safe(reviewTags)) if(tag != null && !tag.isBlank()) tags.save(new ReviewTag(reviewId,tag.trim().replace("#","")));
    }
    private String firstImage(List<String> urls) {
        return safe(urls).stream().filter(url -> url != null && !url.isBlank()).findFirst().orElse(null);
    }
    private List<String> safe(List<String> value) {
        return value == null ? List.of() : value;
    }
    private void requireMember(Long id) {
        if (!members.existsById(id)) throw new IllegalArgumentException("Member not found.");
    }
    // 코스 리뷰는 첫 방문 장소를 대표 장소로 사용하고, 코스 소유권을 검증한다.
    private Long resolveReviewPlaceId(Long memberId, Long courseId, Long placeId) {
        if (courseId == null) {
            if (placeId == null) throw new IllegalArgumentException("Select a course or a place.");
            return placeId;
        }
        TravelCourse course = courses.findById(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found."));
        if (!Objects.equals(course.getMemberId(), memberId)) throw new IllegalArgumentException("You can review only your own course.");
        return courseDetails.findByCourseIdOrderByDayNoAscPlaceOrderAsc(courseId).stream() .findFirst().map(CourseDetail::getPlaceId) .orElseThrow(() -> new IllegalArgumentException("The selected course has no places."));
    }
    private void requirePlace(Long id) {
        places.findById(id).filter(p -> "Y".equals(p.getIsActive())).orElseThrow(() -> new IllegalArgumentException("Place not found."));
    }
    private Review activeReview(Long id) {
        return reviews.findById(id).filter(r -> "N".equals(r.getIsDeleted())).orElseThrow(() -> new IllegalArgumentException("Review not found."));
    }
    private void requireOwner(Review review, Long memberId) {
        if (!Objects.equals(review.getMemberId(), memberId)) throw new IllegalArgumentException("Only the author can change this review.");
    }
    private void refreshPlaceStats(Long id) {
        places.findById(id).ifPresent(p -> p.updateReviewStats(reviews.averageRatingByPlaceId(id), reviews.countByPlaceIdAndIsDeleted(id,"N")));
    }
    private Sort toSort(String sort) {
        if("views".equals(sort)) return Sort.by(Sort.Direction.DESC,"viewCount");
        if("rating".equals(sort)) return Sort.by(Sort.Direction.DESC,"rating").and(Sort.by(Sort.Direction.DESC,"createdAt"));
        return Sort.by(Sort.Direction.DESC,"createdAt");
    }
    private String normalize(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }}
