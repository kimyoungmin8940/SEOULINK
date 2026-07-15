package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.entity.CourseDetail;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import org.springframework.stereotype.Service;

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
