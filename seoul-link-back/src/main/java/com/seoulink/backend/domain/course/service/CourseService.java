package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
import com.seoulink.backend.domain.course.entity.CourseDetail;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 저장된 여행 코스와 날짜별 장소 순서를 조회한다.
 */
@Service
public class CourseService {

    private final TravelCourseRepository travelCourseRepository;
    private final CourseDetailRepository courseDetailRepository;

    public CourseService(
            TravelCourseRepository travelCourseRepository,
            CourseDetailRepository courseDetailRepository
    ) {
        this.travelCourseRepository = travelCourseRepository;
        this.courseDetailRepository = courseDetailRepository;
    }

    /**
     * 코스 기본정보와 날짜·방문 순서대로 정렬된 장소를 함께 반환한다.
     * 장소명·주소·이미지는 PLACES 도메인 통합 후 placeId로 보강한다.
     */
    public CourseDetailResponse getCourse(Long courseId) {
        if (courseId == null || courseId < 1) {
            throw new IllegalArgumentException("코스 ID는 1 이상이어야 합니다.");
        }

        // 코스 기본 행과 상세 장소 행을 각각 조회한 뒤 하나의 상세 응답으로 조립한다.
        TravelCourse course = travelCourseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException(
                        "코스를 찾을 수 없습니다. courseId=" + courseId
                ));
        List<CourseDetail> details =
                courseDetailRepository.findByCourseIdOrderByDayNoAscPlaceOrderAsc(
                        courseId
                );
        List<CoursePlaceResponse> places = details.stream()
                .map(this::toPlaceResponse)
                .toList();
        int dayCount = (int) details.stream()
                .map(CourseDetail::getDayNo)
                .distinct()
                .count();

        return CourseDetailResponse.builder()
                .courseId(course.getCourseId())
                .title(course.getTitle())
                .description(course.getDescription())
                .travelCode(course.getTravelCode())
                .courseType(course.getCourseType())
                .region(course.getRegion())
                .publicCourse("Y".equalsIgnoreCase(course.getPublicStatus()))
                .viewCount(course.getViewCount())
                .placeCount(places.size())
                .dayCount(dayCount)
                .totalDistanceKm(course.getTotalDistanceKm())
                .totalTravelTimeMinutes(course.getTotalTravelTimeMinutes())
                .totalVisitTimeMinutes(course.getTotalVisitTimeMinutes())
                .totalCourseTimeMinutes(course.getTotalCourseTimeMinutes())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .places(places)
                .build();
    }

    /** 회원에게 설문 기반으로 추천·저장된 코스 목록을 최신순으로 반환한다. */
    @Transactional(readOnly = true)
    public List<CourseRecommendationResponse> getRecommendedCourses(Long memberId) {
        validateMemberId(memberId);
        return travelCourseRepository
                .findByMemberIdAndCourseTypeOrderByCreatedAtDesc(
                        memberId,
                        "SURVEY"
                )
                .stream()
                .map(this::toRecommendationResponse)
                .toList();
    }

    /** 회원이 보유한 모든 코스를 유형과 관계없이 최신순으로 반환한다. */
    @Transactional(readOnly = true)
    public List<CourseRecommendationResponse> getMemberCourses(Long memberId) {
        validateMemberId(memberId);
        return travelCourseRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(this::toRecommendationResponse)
                .toList();
    }

    /** 저장 엔티티를 추천·내 코스 목록 카드에서 사용하는 요약 응답으로 변환한다. */
    private CourseRecommendationResponse toRecommendationResponse(
            TravelCourse course
    ) {
        List<CourseDetail> details =
                courseDetailRepository.findByCourseIdOrderByDayNoAscPlaceOrderAsc(
                        course.getCourseId()
                );
        int dayCount = (int) details.stream()
                .map(CourseDetail::getDayNo)
                .distinct()
                .count();
        List<String> regions = course.getRegion() == null
                || course.getRegion().isBlank()
                ? List.of()
                : List.of(course.getRegion());

        return CourseRecommendationResponse.builder()
                .courseId(course.getCourseId())
                .title(course.getTitle())
                .description(course.getDescription())
                .regions(regions)
                .placeCount(details.size())
                .dayCount(dayCount)
                .totalDistanceKm(course.getTotalDistanceKm())
                .totalTravelTimeMinutes(course.getTotalTravelTimeMinutes())
                .totalVisitTimeMinutes(course.getTotalVisitTimeMinutes())
                .totalCourseTimeMinutes(course.getTotalCourseTimeMinutes())
                // 코스 하트 테이블 연동 전까지는 미선택 상태로 반환한다.
                .liked(false)
                .build();
    }

    /** 로그인 연동 전 쿼리 파라미터로 받은 회원 ID의 최소 형식을 검증한다. */
    private void validateMemberId(Long memberId) {
        if (memberId == null || memberId < 1) {
            throw new IllegalArgumentException("회원 ID는 1 이상이어야 합니다.");
        }
    }

    /** 상세 장소 엔티티의 저장 필드를 프론트 조회 응답 필드로 변환한다. */
    private CoursePlaceResponse toPlaceResponse(CourseDetail detail) {
        return CoursePlaceResponse.builder()
                .detailId(detail.getDetailId())
                .placeId(detail.getPlaceId())
                .dayNo(detail.getDayNo())
                .visitDate(detail.getVisitDate())
                .visitOrder(detail.getPlaceOrder())
                .memo(detail.getMemo())
                .visitTime(detail.getVisitTime())
                .expectedVisitMinutes(detail.getStayMinutes())
                .distanceFromPreviousKm(detail.getDistanceFromPreviousKm())
                .travelTimeFromPreviousMinutes(
                        detail.getTravelTimeFromPreviousMinutes()
                )
                .build();
    }
}
