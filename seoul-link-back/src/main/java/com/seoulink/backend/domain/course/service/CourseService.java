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
import java.time.LocalDate;
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
     *
     * <p>현재 Place 엔티티와 Repository가 구현 전이므로 COURSE_DETAILS에 저장된
     * 일정 정보만 반환한다. 장소 도메인 통합 시 placeId 목록으로 PLACES를 일괄 조회해
     * 장소명·카테고리·주소·이미지·좌표·테마를 함께 채워야 한다.</p>
     */
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourse(Long courseId) {
        validateCourseId(courseId);

        // 회원 ID 없이 조회하는 일반 상세 API는 공개 코스만 반환한다.
        TravelCourse course = travelCourseRepository.findById(courseId)
                .filter(savedCourse -> "Y".equalsIgnoreCase(
                        savedCourse.getPublicStatus()
                ))
                .orElseThrow(() -> courseNotFound(courseId));

        return toDetailResponse(course);
    }

    /** 로그인 회원에게 본인 저장 코스 또는 공개 코스의 상세 일정을 반환한다. */
    @Transactional(readOnly = true)
    public CourseDetailResponse getMemberCourse(Long courseId, Long memberId) {
        validateCourseId(courseId);
        validateMemberId(memberId);

        TravelCourse course = travelCourseRepository
                .findByCourseIdAndMemberId(courseId, memberId)
                // 로그인 회원도 다른 사람이 공개한 코스는 정상적으로 볼 수 있다.
                .orElseGet(() -> travelCourseRepository.findById(courseId)
                        .filter(savedCourse -> "Y".equalsIgnoreCase(
                                savedCourse.getPublicStatus()
                        ))
                        .orElseThrow(() -> courseNotFound(courseId)));

        return toDetailResponse(course);
    }

    /** 코스 기본 행과 상세 장소 행을 하나의 상세 응답으로 조립한다. */
    private CourseDetailResponse toDetailResponse(TravelCourse course) {
        Long courseId = course.getCourseId();
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
                .transportMode(course.getTransportMode())
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
        LocalDate startDate = details.stream()
                .map(CourseDetail::getVisitDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate endDate = details.stream()
                .map(CourseDetail::getVisitDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        List<String> regions = course.getRegion() == null
                || course.getRegion().isBlank()
                ? List.of()
                : List.of(course.getRegion());

        return CourseRecommendationResponse.builder()
                .courseId(course.getCourseId())
                .title(course.getTitle())
                .description(course.getDescription())
                .courseType(course.getCourseType())
                .transportMode(course.getTransportMode())
                .regions(regions)
                .placeCount(details.size())
                .dayCount(dayCount)
                .startDate(startDate)
                .endDate(endDate)
                .totalDistanceKm(course.getTotalDistanceKm())
                .totalTravelTimeMinutes(course.getTotalTravelTimeMinutes())
                .totalVisitTimeMinutes(course.getTotalVisitTimeMinutes())
                .totalCourseTimeMinutes(course.getTotalCourseTimeMinutes())
                .createdAt(course.getCreatedAt())
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

    /** 잘못된 코스 식별자가 Repository까지 전달되지 않도록 공통 검증한다. */
    private void validateCourseId(Long courseId) {
        if (courseId == null || courseId < 1) {
            throw new IllegalArgumentException("코스 ID는 1 이상이어야 합니다.");
        }
    }

    /** 비공개 코스 존재 여부나 소유 회원 정보를 외부에 노출하지 않는 404 예외이다. */
    private NoSuchElementException courseNotFound(Long courseId) {
        return new NoSuchElementException(
                "코스를 찾을 수 없습니다. courseId=" + courseId
        );
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

    /**
     * 상세 장소 엔티티의 저장 필드를 프론트 조회 응답 필드로 변환한다.
     *
     * <p>TODO: PlaceRepository 구현 후 N+1 조회가 생기지 않도록 getCourse에서
     * placeId를 모아 한 번에 조회하고, 이 응답의 장소 표시 필드를 보강한다.</p>
     */
    private CoursePlaceResponse toPlaceResponse(CourseDetail detail) {
        // 저장한 이전→현재 구간의 경로 종류를 거리·시간과 함께 상세 화면까지 전달한다.
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
                .transitPathType(detail.getTransitPathType())
                .build();
    }
}
