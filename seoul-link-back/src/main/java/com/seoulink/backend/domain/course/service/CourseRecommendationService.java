package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.CourseRecommendRequest;
import com.seoulink.backend.domain.course.dto.request.DailyPlanRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** 날짜별 추천 후보를 최적화하고 프론트 표시 구조로 변환한다. */
@Service
public class CourseRecommendationService {

    private final CourseOptimizationService courseOptimizationService;

    public CourseRecommendationService(
            CourseOptimizationService courseOptimizationService
    ) {
        this.courseOptimizationService = courseOptimizationService;
    }

    /**
     * 확정 요청 JSON의 날짜 그룹을 평면 최적화 입력으로 변환한 뒤 날짜별 결과를 반환한다.
     *
     * <p>이 API에서는 추천 결과를 바로 DB에 저장하지 않는다. 사용자가 결과 화면에서
     * 코스 담기를 선택하면 기존 {@code POST /api/courses} 저장 API를 호출한다.</p>
     */
    public CourseRecommendResponse recommend(CourseRecommendRequest request) {
        ValidatedRecommendation validated = validateAndFlatten(request);

        CourseOptimizeResponse optimized = courseOptimizationService.optimize(
                CourseOptimizeRequest.builder()
                        .placeCandidates(validated.placeCandidates())
                        .build()
        );

        List<CourseDayResponse> days = toDayResponses(
                optimized.getOptimizedPlaces(),
                request.getDailyStartTime()
        );

        return CourseRecommendResponse.builder()
                .resultId(request.getResultId())
                .dailyStartTime(request.getDailyStartTime())
                .placeCount(optimized.getOptimizedPlaces().size())
                .dayCount(days.size())
                .totalDistanceKm(round(optimized.getTotalDistanceKm(), 3))
                .totalTravelTimeMinutes(round(
                        optimized.getTotalTravelTimeMinutes(),
                        2
                ))
                .totalVisitTimeMinutes(optimized.getTotalVisitTimeMinutes())
                .totalCourseTimeMinutes(round(
                        optimized.getTotalCourseTimeMinutes(),
                        2
                ))
                .days(days)
                .build();
    }

    /** 이전 호출부가 남아 있어도 컴파일되도록 추천 메서드 이름을 한동안 호환한다. */
    @Deprecated
    public CourseRecommendResponse recommendAndSave(
            CourseRecommendRequest request
    ) {
        return recommend(request);
    }

    /** 요청 필수값을 검증하고 날짜 그룹의 날짜를 장소와 대체 후보에 복사한다. */
    private ValidatedRecommendation validateAndFlatten(
            CourseRecommendRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("추천 코스 생성 요청은 null일 수 없습니다.");
        }
        if (request.getResultId() == null || request.getResultId() < 1) {
            throw new IllegalArgumentException("설문 결과 ID는 1 이상이어야 합니다.");
        }
        if (request.getDailyStartTime() == null) {
            throw new IllegalArgumentException("일정 시작 시각은 필수입니다.");
        }
        if (request.getDailyPlans() == null || request.getDailyPlans().isEmpty()) {
            throw new IllegalArgumentException("날짜별 일정이 한 개 이상 필요합니다.");
        }

        List<PlaceCandidateDto> flattened = new ArrayList<>();
        Set<LocalDate> visitDates = new HashSet<>();

        for (DailyPlanRequest dailyPlan : request.getDailyPlans()) {
            if (dailyPlan == null) {
                throw new IllegalArgumentException("날짜별 일정은 null일 수 없습니다.");
            }
            LocalDate visitDate = dailyPlan.getVisitDate();
            if (visitDate == null) {
                throw new IllegalArgumentException("날짜별 일정의 방문 날짜는 필수입니다.");
            }
            if (!visitDates.add(visitDate)) {
                throw new IllegalArgumentException(
                        "동일한 방문 날짜를 dailyPlans에 중복해서 보낼 수 없습니다. visitDate="
                                + visitDate
                );
            }
            if (dailyPlan.getPlaceCandidates() == null
                    || dailyPlan.getPlaceCandidates().isEmpty()) {
                throw new IllegalArgumentException(
                        "각 날짜에는 장소 후보가 한 개 이상 필요합니다. visitDate="
                                + visitDate
                );
            }

            for (PlaceCandidateDto candidate : dailyPlan.getPlaceCandidates()) {
                flattened.add(copyCandidateWithDate(candidate, visitDate, true));
            }
        }

        return new ValidatedRecommendation(flattened);
    }

    /** 원본 요청을 변경하지 않고 날짜와 중첩 대체 후보를 포함한 복사본을 만든다. */
    private PlaceCandidateDto copyCandidateWithDate(
            PlaceCandidateDto source,
            LocalDate visitDate,
            boolean includeAlternatives
    ) {
        if (source == null) {
            throw new IllegalArgumentException("장소 후보는 null일 수 없습니다.");
        }

        List<PlaceCandidateDto> alternatives = new ArrayList<>();
        if (includeAlternatives && source.getAlternativeCandidates() != null) {
            for (PlaceCandidateDto alternative : source.getAlternativeCandidates()) {
                alternatives.add(copyCandidateWithDate(
                        alternative,
                        visitDate,
                        false
                ));
            }
        }

        return PlaceCandidateDto.builder()
                .placeId(source.getPlaceId())
                .placeName(source.getPlaceName())
                .category(source.getCategory())
                .recommendationScore(source.getRecommendationScore())
                .latitude(source.getLatitude())
                .longitude(source.getLongitude())
                .visitDate(visitDate)
                .themePalaceCultureYn(normalizeYn(source.getThemePalaceCultureYn(), "themePalaceCultureYn"))
                .themeNatureHangangYn(normalizeYn(source.getThemeNatureHangangYn(), "themeNatureHangangYn"))
                .themeDateYn(normalizeYn(source.getThemeDateYn(), "themeDateYn"))
                .themeFoodTourYn(normalizeYn(source.getThemeFoodTourYn(), "themeFoodTourYn"))
                .themeCafeTourYn(normalizeYn(source.getThemeCafeTourYn(), "themeCafeTourYn"))
                .themeShoppingHotplaceYn(normalizeYn(source.getThemeShoppingHotplaceYn(), "themeShoppingHotplaceYn"))
                .themeNightViewYn(normalizeYn(source.getThemeNightViewYn(), "themeNightViewYn"))
                .themeHotelStayYn(normalizeYn(source.getThemeHotelStayYn(), "themeHotelStayYn"))
                .alternativeCandidates(alternatives)
                .build();
    }

    /** 최적화된 평면 장소 목록을 프론트 공통 계약인 날짜별 구조로 변환한다. */
    private List<CourseDayResponse> toDayResponses(
            List<OptimizedPlaceDto> optimizedPlaces,
            LocalTime dailyStartTime
    ) {
        Map<LocalDate, List<OptimizedPlaceDto>> placesByDate = new TreeMap<>();
        for (OptimizedPlaceDto place : optimizedPlaces) {
            placesByDate
                    .computeIfAbsent(place.getVisitDate(), ignored -> new ArrayList<>())
                    .add(place);
        }

        List<CourseDayResponse> days = new ArrayList<>();
        int dayNo = 1;
        for (Map.Entry<LocalDate, List<OptimizedPlaceDto>> entry
                : placesByDate.entrySet()) {
            List<OptimizedPlaceDto> dailyOptimizedPlaces = entry.getValue().stream()
                    .sorted(Comparator.comparing(OptimizedPlaceDto::getVisitOrder))
                    .toList();
            List<CoursePlaceResponse> places = toPlaceResponses(
                    dailyOptimizedPlaces,
                    dailyStartTime
            );

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

    /** 공통 시작 시각부터 이동·체류시간을 누적해 장소별 예상 방문 시각을 만든다. */
    private List<CoursePlaceResponse> toPlaceResponses(
            List<OptimizedPlaceDto> places,
            LocalTime dailyStartTime
    ) {
        List<CoursePlaceResponse> responses = new ArrayList<>();
        LocalTime currentTime = dailyStartTime;

        for (OptimizedPlaceDto place : places) {
            currentTime = currentTime.plusMinutes(Math.round(valueOrZero(
                    place.getTravelTimeFromPreviousMinutes()
            )));
            responses.add(toPlaceResponse(
                    place,
                    currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            ));
            currentTime = currentTime.plusMinutes(valueOrZero(
                    place.getExpectedVisitMinutes()
            ));
        }
        return responses;
    }

    /** 추천 직후 화면에 필요한 표시값과 최적화 계산값을 응답 DTO로 변환한다. */
    private CoursePlaceResponse toPlaceResponse(
            OptimizedPlaceDto place,
            String visitTime
    ) {
        return CoursePlaceResponse.builder()
                .placeId(place.getPlaceId())
                .placeName(place.getPlaceName())
                .category(place.getCategory())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .recommendationScore(place.getRecommendationScore())
                .themePalaceCultureYn(place.getThemePalaceCultureYn())
                .themeNatureHangangYn(place.getThemeNatureHangangYn())
                .themeDateYn(place.getThemeDateYn())
                .themeFoodTourYn(place.getThemeFoodTourYn())
                .themeCafeTourYn(place.getThemeCafeTourYn())
                .themeShoppingHotplaceYn(place.getThemeShoppingHotplaceYn())
                .themeNightViewYn(place.getThemeNightViewYn())
                .themeHotelStayYn(place.getThemeHotelStayYn())
                .visitOrder(place.getVisitOrder())
                .visitTime(visitTime)
                .expectedVisitMinutes(place.getExpectedVisitMinutes())
                .distanceFromPreviousKm(place.getDistanceFromPreviousKm())
                .travelTimeFromPreviousMinutes(
                        place.getTravelTimeFromPreviousMinutes()
                )
                .build();
    }

    /** 테마 여부는 누락 시 N으로 두고 Y/N 외의 값은 요청 오류로 처리한다. */
    private String normalizeYn(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return "N";
        }
        String normalized = value.trim().toUpperCase();
        if (!normalized.equals("Y") && !normalized.equals("N")) {
            throw new IllegalArgumentException(
                    fieldName + "은 Y 또는 N이어야 합니다."
            );
        }
        return normalized;
    }

    private double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 검증과 날짜 전파가 끝난 최적화 입력이다. */
    private record ValidatedRecommendation(
            List<PlaceCandidateDto> placeCandidates
    ) {
    }
}
