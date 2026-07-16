package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
import com.seoulink.backend.domain.course.entity.CourseDetail;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

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
        List<CourseDayResponse> days = toDayResponses(details);

        return CourseDetailResponse.builder()
                .courseId(course.getCourseId())
                .title(course.getTitle())
                .description(course.getDescription())
                .travelCode(course.getTravelCode())
                .courseType(course.getCourseType())
                .region(course.getRegion())
                .publicCourse("Y".equalsIgnoreCase(course.getPublicStatus()))
                .viewCount(course.getViewCount())
                .placeCount(details.size())
                .dayCount(days.size())
                .totalDistanceKm(course.getTotalDistanceKm())
                .totalTravelTimeMinutes(course.getTotalTravelTimeMinutes())
                .totalVisitTimeMinutes(course.getTotalVisitTimeMinutes())
                .totalCourseTimeMinutes(course.getTotalCourseTimeMinutes())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .days(days)
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

    /** 저장 상세를 dayNo 기준으로 묶어 추천 결과와 동일한 날짜별 구조를 만든다. */
    private List<CourseDayResponse> toDayResponses(List<CourseDetail> details) {
        Map<Integer, List<CourseDetail>> detailsByDay = new TreeMap<>();
        for (CourseDetail detail : details) {
            detailsByDay
                    .computeIfAbsent(detail.getDayNo(), ignored -> new ArrayList<>())
                    .add(detail);
        }

        return detailsByDay.entrySet().stream()
                .map(entry -> toDayResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** 한 날짜의 상세 장소와 거리·시간 합계를 날짜별 응답으로 변환한다. */
    private CourseDayResponse toDayResponse(
            Integer dayNo,
            List<CourseDetail> dailyDetails
    ) {
        double dailyDistanceKm = dailyDetails.stream()
                .mapToDouble(detail -> valueOrZero(
                        detail.getDistanceFromPreviousKm()
                ))
                .sum();
        double dailyTravelTimeMinutes = dailyDetails.stream()
                .mapToDouble(detail -> valueOrZero(
                        detail.getTravelTimeFromPreviousMinutes()
                ))
                .sum();
        int dailyVisitTimeMinutes = dailyDetails.stream()
                .mapToInt(detail -> valueOrZero(detail.getStayMinutes()))
                .sum();

        return CourseDayResponse.builder()
                .dayNo(dayNo)
                .visitDate(dailyDetails.get(0).getVisitDate())
                .dailyDistanceKm(round(dailyDistanceKm, 3))
                .dailyTravelTimeMinutes(round(dailyTravelTimeMinutes, 2))
                .dailyVisitTimeMinutes(dailyVisitTimeMinutes)
                .dailyCourseTimeMinutes(round(
                        dailyVisitTimeMinutes + dailyTravelTimeMinutes,
                        2
                ))
                .places(dailyDetails.stream()
                        .map(this::toPlaceResponse)
                        .toList())
                .build();
    }

    /** null일 수 있는 소수 저장값을 날짜별 합산에서 안전하게 0으로 처리한다. */
    private double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    /** null일 수 있는 체류시간을 날짜별 합산에서 안전하게 0으로 처리한다. */
    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    /** 날짜별 거리와 시간 합계를 전체 코스 저장 기준과 같은 자릿수로 반올림한다. */
    private double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 상세 장소 엔티티의 저장 필드를 프론트 조회 응답 필드로 변환한다. */
    private CoursePlaceResponse toPlaceResponse(CourseDetail detail) {
        return CoursePlaceResponse.builder()
                .detailId(detail.getDetailId())
                .placeId(detail.getPlaceId())
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
