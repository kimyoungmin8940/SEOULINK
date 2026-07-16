package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.CourseSavePlaceDto;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 추천 후보 최적화부터 최종 코스 저장까지 하나의 서비스 흐름으로 연결한다. */
@Service
public class CourseRecommendationService {

    private final CourseOptimizationService courseOptimizationService;
    private final CourseSaveService courseSaveService;

    public CourseRecommendationService(
            CourseOptimizationService courseOptimizationService,
            CourseSaveService courseSaveService
    ) {
        this.courseOptimizationService = courseOptimizationService;
        this.courseSaveService = courseSaveService;
    }

    /**
     * 추천 장소를 최적화한 뒤 {@code SURVEY} 코스로 저장한다.
     * 저장 중 하나라도 실패하면 코스와 상세 장소 저장을 모두 롤백한다.
     */
    @Transactional
    public CourseRecommendResponse recommendAndSave(CourseRecommendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("추천 코스 생성 요청은 null일 수 없습니다.");
        }

        // 1단계: 추천 담당자가 전달한 후보를 날짜별 최단 이동 경로로 정렬한다.
        CourseOptimizeResponse optimized = courseOptimizationService.optimize(
                CourseOptimizeRequest.builder()
                        .placeCandidates(request.getPlaceCandidates())
                        .alternativeCandidates(request.getAlternativeCandidates())
                        .build()
        );

        // 2단계: 최적화 응답을 COURSE_DETAILS 저장 전용 DTO로 변환한다.
        List<CourseSavePlaceDto> places = optimized.getOptimizedPlaces().stream()
                .map(this::toSavePlace)
                .toList();

        // 3단계: 취향 검사 기반 결과임을 나타내는 SURVEY 유형으로 저장한다.
        CourseSaveResponse saved = courseSaveService.saveOptimizedCourse(
                CourseSaveRequest.builder()
                        .memberId(request.getMemberId())
                        .resultId(request.getResultId())
                        .paymentId(request.getPaymentId())
                        .title(request.getTitle())
                        .description(request.getDescription())
                        .travelCode(request.getTravelCode())
                        .courseType("SURVEY")
                        .region(request.getRegion())
                        .publicCourse(request.getPublicCourse())
                        .places(places)
                        .build()
        );

        // 저장 식별자와 최적화 경로를 함께 반환해 프론트가 즉시 결과를 표시하게 한다.
        return CourseRecommendResponse.builder()
                .courseId(saved.getCourseId())
                .title(saved.getTitle())
                .description(request.getDescription())
                .travelCode(request.getTravelCode())
                .courseType("SURVEY")
                .region(request.getRegion())
                .publicCourse(Boolean.TRUE.equals(request.getPublicCourse()))
                .placeCount(saved.getPlaceCount())
                .dayCount(saved.getDayCount())
                .totalDistanceKm(saved.getTotalDistanceKm())
                .totalTravelTimeMinutes(saved.getTotalTravelTimeMinutes())
                .totalVisitTimeMinutes(saved.getTotalVisitTimeMinutes())
                .totalCourseTimeMinutes(saved.getTotalCourseTimeMinutes())
                .days(toDayResponses(optimized.getOptimizedPlaces()))
                .build();
    }

    /** 최적화된 평면 장소 목록을 프론트 공통 계약인 날짜별 구조로 변환한다. */
    private List<CourseDayResponse> toDayResponses(
            List<OptimizedPlaceDto> optimizedPlaces
    ) {
        Map<LocalDate, List<OptimizedPlaceDto>> placesByDate =
                new TreeMap<>();
        for (OptimizedPlaceDto place : optimizedPlaces) {
            placesByDate
                    .computeIfAbsent(place.getVisitDate(), ignored -> new ArrayList<>())
                    .add(place);
        }

        List<CourseDayResponse> days = new ArrayList<>();
        int dayNo = 1;
        for (Map.Entry<LocalDate, List<OptimizedPlaceDto>> entry
                : placesByDate.entrySet()) {
            List<OptimizedPlaceDto> dailyOptimizedPlaces = entry.getValue();
            List<CoursePlaceResponse> places = dailyOptimizedPlaces.stream()
                    .map(this::toPlaceResponse)
                    .toList();

            double dailyDistanceKm = dailyOptimizedPlaces.stream()
                    .mapToDouble(place -> valueOrZero(
                            place.getDistanceFromPreviousKm()
                    ))
                    .sum();
            double dailyTravelTimeMinutes = dailyOptimizedPlaces.stream()
                    .mapToDouble(place -> valueOrZero(
                            place.getTravelTimeFromPreviousMinutes()
                    ))
                    .sum();
            int dailyVisitTimeMinutes = dailyOptimizedPlaces.stream()
                    .mapToInt(place -> valueOrZero(
                            place.getExpectedVisitMinutes()
                    ))
                    .sum();

            days.add(CourseDayResponse.builder()
                    .dayNo(dayNo++)
                    .visitDate(entry.getKey())
                    .dailyDistanceKm(round(dailyDistanceKm, 3))
                    .dailyTravelTimeMinutes(round(
                            dailyTravelTimeMinutes,
                            2
                    ))
                    .dailyVisitTimeMinutes(dailyVisitTimeMinutes)
                    .dailyCourseTimeMinutes(round(
                            dailyVisitTimeMinutes + dailyTravelTimeMinutes,
                            2
                    ))
                    .places(places)
                    .build());
        }
        return days;
    }

    /** 추천 직후 화면에 필요한 장소 표시값과 최적화 계산값을 공통 DTO로 변환한다. */
    private CoursePlaceResponse toPlaceResponse(OptimizedPlaceDto place) {
        return CoursePlaceResponse.builder()
                .placeId(place.getPlaceId())
                .placeName(place.getPlaceName())
                .category(place.getCategory())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .recommendationScore(place.getRecommendationScore())
                .visitOrder(place.getVisitOrder())
                .expectedVisitMinutes(place.getExpectedVisitMinutes())
                .distanceFromPreviousKm(place.getDistanceFromPreviousKm())
                .travelTimeFromPreviousMinutes(
                        place.getTravelTimeFromPreviousMinutes()
                )
                .build();
    }

    /** null일 수 있는 소수 계산값을 날짜별 합산에서 안전하게 0으로 처리한다. */
    private double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    /** null일 수 있는 방문 시간을 날짜별 합산에서 안전하게 0으로 처리한다. */
    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    /** 거리와 시간 합계를 전체 코스 저장 기준과 같은 자릿수로 반올림한다. */
    private double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 최적화 장소 중 DB 상세 행에 필요한 값만 추려 저장 DTO로 변환한다. */
    private CourseSavePlaceDto toSavePlace(OptimizedPlaceDto place) {
        return CourseSavePlaceDto.builder()
                .placeId(place.getPlaceId())
                .visitDate(place.getVisitDate())
                .visitOrder(place.getVisitOrder())
                .expectedVisitMinutes(place.getExpectedVisitMinutes())
                .distanceFromPreviousKm(place.getDistanceFromPreviousKm())
                .travelTimeFromPreviousMinutes(
                        place.getTravelTimeFromPreviousMinutes()
                )
                .build();
    }
}
