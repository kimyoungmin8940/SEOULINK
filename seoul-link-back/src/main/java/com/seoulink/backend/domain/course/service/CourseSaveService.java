package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseSavePlaceDto;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 최적화가 끝난 코스와 날짜별 장소 순서를 하나의 트랜잭션으로 저장한다. */
@Service
public class CourseSaveService {

    private static final Set<String> ALLOWED_COURSE_TYPES =
            Set.of("CUSTOM", "SURVEY", "CHATBOT");

    private final TravelCourseRepository travelCourseRepository;
    private final CourseDetailRepository courseDetailRepository;

    public CourseSaveService(
            TravelCourseRepository travelCourseRepository,
            CourseDetailRepository courseDetailRepository
    ) {
        this.travelCourseRepository = travelCourseRepository;
        this.courseDetailRepository = courseDetailRepository;
    }

    /**
     * 코스 기본 정보를 먼저 저장한 뒤 생성된 코스 ID로 상세 장소를 저장한다.
     * 클라이언트가 합계값을 전달하지 않아도 장소별 계산값을 다시 합산한다.
     */
    @Transactional
    public CourseSaveResponse saveOptimizedCourse(CourseSaveRequest request) {
        ValidatedCourse validated = validateAndNormalize(request);

        double totalDistanceKm = validated.places().stream()
                .mapToDouble(CourseSavePlaceDto::getDistanceFromPreviousKm)
                .sum();
        double totalTravelTimeMinutes = validated.places().stream()
                .mapToDouble(CourseSavePlaceDto::getTravelTimeFromPreviousMinutes)
                .sum();
        int totalVisitTimeMinutes = validated.places().stream()
                .mapToInt(CourseSavePlaceDto::getExpectedVisitMinutes)
                .sum();

        double storedTotalDistanceKm = round(totalDistanceKm, 3);
        double storedTotalTravelTimeMinutes = round(totalTravelTimeMinutes, 2);
        double storedTotalCourseTimeMinutes = round(
                totalVisitTimeMinutes + totalTravelTimeMinutes,
                2
        );

        TravelCourse savedCourse = travelCourseRepository.save(
                TravelCourse.builder()
                        .memberId(request.getMemberId())
                        .resultId(request.getResultId())
                        .paymentId(request.getPaymentId())
                        .title(validated.title())
                        .description(trimToNull(request.getDescription()))
                        .travelCode(validated.travelCode())
                        .courseType(validated.courseType())
                        .region(trimToNull(request.getRegion()))
                        .publicStatus(Boolean.TRUE.equals(request.getPublicCourse()) ? "Y" : "N")
                        .viewCount(0L)
                        .totalDistanceKm(storedTotalDistanceKm)
                        .totalTravelTimeMinutes(storedTotalTravelTimeMinutes)
                        .totalVisitTimeMinutes(totalVisitTimeMinutes)
                        .totalCourseTimeMinutes(storedTotalCourseTimeMinutes)
                        .build()
        );

        if (savedCourse.getCourseId() == null) {
            throw new IllegalStateException("저장된 코스 ID를 확인할 수 없습니다.");
        }

        Map<LocalDate, Integer> dayNumbers = createDayNumbers(validated.places());
        List<CourseDetail> details = validated.places().stream()
                .map(place -> CourseDetail.builder()
                        .courseId(savedCourse.getCourseId())
                        .placeId(place.getPlaceId())
                        .dayNo(dayNumbers.get(place.getVisitDate()))
                        .placeOrder(place.getVisitOrder())
                        .stayMinutes(place.getExpectedVisitMinutes())
                        .visitDate(place.getVisitDate())
                        .distanceFromPreviousKm(round(
                                place.getDistanceFromPreviousKm(),
                                3
                        ))
                        .travelTimeFromPreviousMinutes(round(
                                place.getTravelTimeFromPreviousMinutes(),
                                2
                        ))
                        .build())
                .toList();

        courseDetailRepository.saveAll(details);

        return CourseSaveResponse.builder()
                .courseId(savedCourse.getCourseId())
                .title(savedCourse.getTitle())
                .placeCount(details.size())
                .dayCount(dayNumbers.size())
                .totalDistanceKm(storedTotalDistanceKm)
                .totalTravelTimeMinutes(storedTotalTravelTimeMinutes)
                .totalVisitTimeMinutes(totalVisitTimeMinutes)
                .totalCourseTimeMinutes(storedTotalCourseTimeMinutes)
                .build();
    }

    private ValidatedCourse validateAndNormalize(CourseSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("코스 저장 요청은 null일 수 없습니다.");
        }
        validatePositiveId(request.getMemberId(), "회원 ID");
        validateOptionalPositiveId(request.getResultId(), "설문 결과 ID");
        validateOptionalPositiveId(request.getPaymentId(), "결제 ID");

        String title = requireText(request.getTitle(), "코스 제목");
        if (title.length() > 200) {
            throw new IllegalArgumentException("코스 제목은 200자를 초과할 수 없습니다.");
        }

        String courseType = normalizeCourseType(request.getCourseType());
        String travelCode = normalizeTravelCode(request.getTravelCode());

        List<CourseSavePlaceDto> places = request.getPlaces();
        if (places == null || places.isEmpty()) {
            throw new IllegalArgumentException("저장할 코스 장소가 한 개 이상 필요합니다.");
        }

        List<CourseSavePlaceDto> sortedPlaces = new ArrayList<>(places);
        for (CourseSavePlaceDto place : sortedPlaces) {
            validatePlace(place);
        }
        sortedPlaces.sort(Comparator
                .comparing(CourseSavePlaceDto::getVisitDate)
                .thenComparing(CourseSavePlaceDto::getVisitOrder));
        validateSequentialOrders(sortedPlaces);

        return new ValidatedCourse(title, courseType, travelCode, sortedPlaces);
    }

    private void validatePlace(CourseSavePlaceDto place) {
        if (place == null) {
            throw new IllegalArgumentException("저장할 장소는 null일 수 없습니다.");
        }
        validatePositiveId(place.getPlaceId(), "장소 ID");
        if (place.getVisitDate() == null) {
            throw new IllegalArgumentException("장소 방문 날짜는 필수입니다.");
        }
        if (place.getVisitOrder() == null || place.getVisitOrder() < 1) {
            throw new IllegalArgumentException("장소 방문 순서는 1 이상이어야 합니다.");
        }
        if (place.getExpectedVisitMinutes() == null
                || place.getExpectedVisitMinutes() < 0) {
            throw new IllegalArgumentException("예상 방문 시간은 0분 이상이어야 합니다.");
        }
        validateNonNegativeFinite(
                place.getDistanceFromPreviousKm(),
                "이전 장소로부터의 거리"
        );
        validateNonNegativeFinite(
                place.getTravelTimeFromPreviousMinutes(),
                "이전 장소로부터의 이동시간"
        );
    }

    private void validateSequentialOrders(List<CourseSavePlaceDto> places) {
        LocalDate currentDate = null;
        int expectedOrder = 1;

        for (CourseSavePlaceDto place : places) {
            if (!place.getVisitDate().equals(currentDate)) {
                currentDate = place.getVisitDate();
                expectedOrder = 1;
            }
            if (place.getVisitOrder() != expectedOrder) {
                throw new IllegalArgumentException(
                        "날짜별 방문 순서는 1부터 빠짐없이 이어져야 합니다."
                );
            }
            expectedOrder++;
        }
    }

    private Map<LocalDate, Integer> createDayNumbers(List<CourseSavePlaceDto> places) {
        Map<LocalDate, Integer> dayNumbers = new LinkedHashMap<>();
        for (CourseSavePlaceDto place : places) {
            dayNumbers.computeIfAbsent(
                    place.getVisitDate(),
                    ignored -> dayNumbers.size() + 1
            );
        }
        return dayNumbers;
    }

    private String normalizeCourseType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "CUSTOM";
        }

        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_COURSE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "코스 유형은 CUSTOM, SURVEY, CHATBOT 중 하나여야 합니다."
            );
        }
        return normalized;
    }

    private String normalizeTravelCode(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{5}")) {
            throw new IllegalArgumentException("여행 유형 코드는 영문 대문자 5자리여야 합니다.");
        }
        return normalized;
    }

    private void validatePositiveId(Long value, String fieldName) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(fieldName + "는 1 이상이어야 합니다.");
        }
    }

    private void validateOptionalPositiveId(Long value, String fieldName) {
        if (value != null) {
            validatePositiveId(value, fieldName);
        }
    }

    private void validateNonNegativeFinite(Double value, String fieldName) {
        if (value == null || !Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + "은 0 이상의 유한한 숫자여야 합니다.");
        }
    }

    private String requireText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record ValidatedCourse(
            String title,
            String courseType,
            String travelCode,
            List<CourseSavePlaceDto> places
    ) {
    }
}
