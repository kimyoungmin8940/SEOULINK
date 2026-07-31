package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseBatchSaveRequest;
import com.seoulink.backend.domain.course.dto.request.CourseSavePlaceDto;
import com.seoulink.backend.domain.course.dto.request.CourseSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseBatchSaveResponse;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import com.seoulink.backend.domain.course.entity.CourseDetail;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 최적화가 끝난 코스와 날짜별 장소 순서를 트랜잭션으로 저장한다. */
@Service
public class CourseSaveService {

    private static final Set<String> ALLOWED_COURSE_TYPES =
            Set.of("CUSTOM", "SURVEY", "CHATBOT", "THEME");
    private static final int MAX_BATCH_COURSE_COUNT = 3;

    private final TravelCourseRepository travelCourseRepository;
    private final CourseDetailRepository courseDetailRepository;

    public CourseSaveService(
            TravelCourseRepository travelCourseRepository,
            CourseDetailRepository courseDetailRepository
    ) {
        this.travelCourseRepository = travelCourseRepository;
        this.courseDetailRepository = courseDetailRepository;
    }

    /** 사용자가 확정한 코스 한 건과 상세 장소를 하나의 트랜잭션으로 저장한다. */
    @Transactional
    public CourseSaveResponse saveOptimizedCourse(CourseSaveRequest request) {
        ValidatedCourse validated = validateAndNormalize(request);
        return saveValidatedCourse(request, validated, true);
    }

    /**
     * 사용자가 선택한 복수 코스를 한 번에 저장한다.
     *
     * <p>모든 요청을 먼저 검증한 뒤 저장하며, 코스 또는 상세 장소 중 하나라도
     * 저장에 실패하면 메서드 전체가 롤백되어 일부 코스만 남지 않는다.</p>
     */
    @Transactional
    public CourseBatchSaveResponse saveOptimizedCourses(
            CourseBatchSaveRequest batchRequest
    ) {
        List<CourseSaveRequest> requests = validateBatchRequest(batchRequest);

        // DB 쓰기 전에 전체 요청 검증을 끝내 부분 저장 가능성을 줄인다.
        List<ValidatedCourse> validatedCourses = requests.stream()
                .map(this::validateAndNormalize)
                .toList();
        validateSameMemberAndTransportMode(requests);

        List<CourseSaveResponse> savedCourses = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            savedCourses.add(saveValidatedCourse(
                    requests.get(index),
                    validatedCourses.get(index),
                    true
            ));
        }

        return CourseBatchSaveResponse.builder()
                .savedCount(savedCourses.size())
                .savedCourses(savedCourses)
                .build();
    }

    /**
     * 추천 API가 사용자에게 보여준 모든 옵션을 저장 전 추천 이력으로 기록한다.
     *
     * <p>같은 추천 조합이 재호출되어도 기존 행을 재사용하고, 이 단계에서는
     * 내 코스에 포함되지 않도록 IS_SAVED를 N으로 저장한다. 반환한 코스 ID는
     * 실제 경로 조회가 끝난 뒤 같은 이력 행을 갱신하는 식별자로 사용한다.</p>
     */
    @Transactional
    public List<CourseSaveResponse> saveRecommendationHistory(
            List<CourseSaveRequest> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<ValidatedCourse> validatedCourses = requests.stream()
                .map(this::validateAndNormalize)
                .toList();
        validateSameMemberAndTransportMode(requests);

        List<CourseSaveResponse> savedCourses = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            savedCourses.add(saveValidatedCourse(
                    requests.get(index),
                    validatedCourses.get(index),
                    false
            ));
        }
        return List.copyOf(savedCourses);
    }

    /** 검증이 끝난 코스 한 건의 기본 정보와 상세 장소를 저장하고 합계를 반환한다. */
    private CourseSaveResponse saveValidatedCourse(
            CourseSaveRequest request,
            ValidatedCourse validated,
            boolean markAsSaved
    ) {
        // SOURCE_COURSE_KEY는 테마 코스 전용이다. 설문 추천 이력은 항상 null로
        // 저장해 테마 북마크용 중복 키가 추천 코스 저장을 막지 않게 한다.
        String sourceCourseKey = "THEME".equals(validated.courseType())
                ? trimToNull(request.getSourceCourseKey())
                : null;

        if ("THEME".equals(validated.courseType())) {
            TravelCourse existingThemeCourse = travelCourseRepository
                    .findByMemberIdAndSourceCourseKey(
                            request.getMemberId(),
                            sourceCourseKey
                    )
                    .orElse(null);

            if (existingThemeCourse != null) {
                if (existingThemeCourse.isSaved()) {
                    throw new IllegalArgumentException("이미 저장한 테마 코스입니다.");
                }

                return refreshExistingHistoryCourse(
                        existingThemeCourse,
                        request,
                        validated,
                        true
                );
            }
        }

        CourseSaveResponse targetedCourse = refreshTargetedHistoryCourse(
                request,
                validated,
                markAsSaved
        );
        if (targetedCourse != null) {
            return targetedCourse;
        }

        if (markAsSaved
                && "SURVEY".equals(validated.courseType())
                && request.getCourseId() == null) {
            throw new IllegalArgumentException(
                    "저장할 추천 코스의 정확한 courseId가 필요합니다."
            );
        }

        CourseSaveResponse existingCourse = findExistingSurveyCourse(
                request,
                validated,
                markAsSaved
        );
        if (existingCourse != null) {
            return existingCourse;
        }

        CourseTotals totals = calculateTotals(validated);

        TravelCourse savedCourse = travelCourseRepository.save(
                TravelCourse.builder()
                        .memberId(request.getMemberId())
                        .resultId(request.getResultId())
                        .paymentId(request.getPaymentId())
                        .title(validated.title())
                        .description(trimToNull(request.getDescription()))
                        .travelCode(validated.travelCode())
                        .courseType(validated.courseType())
                        .sourceCourseKey(sourceCourseKey)
                        .region(trimToNull(request.getRegion()))
                        .publicStatus(Boolean.TRUE.equals(request.getPublicCourse()) ? "Y" : "N")
                        .viewCount(0L)
                        .savedStatus(markAsSaved ? "Y" : "N")
                        .totalDistanceKm(totals.distanceKm())
                        .totalTravelTimeMinutes(totals.travelMinutes())
                        .totalVisitTimeMinutes(totals.visitMinutes())
                        .totalCourseTimeMinutes(totals.courseMinutes())
                        .build()
        );

        if (savedCourse.getCourseId() == null) {
            throw new IllegalStateException("저장된 코스 ID를 확인할 수 없습니다.");
        }

        List<CourseDetail> details = buildDetails(
                savedCourse.getCourseId(),
                validated
        );
        courseDetailRepository.saveAll(details);

        return toSaveResponse(
                savedCourse,
                validated,
                details,
                totals
        );
    }

    /**
     * 추천 응답에 실려 온 courseId로 정확한 추천 이력 행을 찾아 실제 경로값을
     * 갱신한다. 장소 교체·삭제로 구성이 달라져도 다른 추천 코스를 건드리지 않는다.
     */
    private CourseSaveResponse refreshTargetedHistoryCourse(
            CourseSaveRequest request,
            ValidatedCourse validated,
            boolean markAsSaved
    ) {
        if (request.getCourseId() == null
                || !"SURVEY".equals(validated.courseType())
                || request.getResultId() == null) {
            return null;
        }

        TravelCourse course = travelCourseRepository
                .findByCourseIdAndMemberId(
                        request.getCourseId(),
                        request.getMemberId()
                )
                .filter(candidate -> "SURVEY".equalsIgnoreCase(
                        candidate.getCourseType()
                ))
                .filter(candidate -> request.getResultId().equals(
                        candidate.getResultId()
                ))
                .orElse(null);

        return course == null
                ? null
                : refreshExistingHistoryCourse(
                        course,
                        request,
                        validated,
                        markAsSaved
                );
    }

    /**
     * 같은 회원이 같은 설문 결과에서 동일한 장소 구성의 추천 코스를 이미
     * 받았다면 새 행을 만들지 않는다. 사용자가 저장을 누른 호출이면 기존
     * 추천 이력 행만 저장 상태로 전환한다.
     */
    private CourseSaveResponse findExistingSurveyCourse(
            CourseSaveRequest request,
            ValidatedCourse validated,
            boolean markAsSaved
    ) {
        if (!"SURVEY".equals(validated.courseType())
                || request.getResultId() == null) {
            return null;
        }

        String requestedCompositionKey = createCompositionKey(
                validated.places()
        );
        List<TravelCourse> candidates = travelCourseRepository
                .findByMemberIdAndResultIdAndCourseTypeOrderByCreatedAtDesc(
                        request.getMemberId(),
                        request.getResultId(),
                        "SURVEY"
                );

        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        for (TravelCourse candidate : candidates) {
            if (candidate.getCourseId() == null) {
                continue;
            }

            List<CourseDetail> details = courseDetailRepository
                    .findByCourseIdOrderByDayNoAscPlaceOrderAsc(
                            candidate.getCourseId()
                    );
            if (!requestedCompositionKey.equals(
                    createCompositionKeyFromDetails(details)
            )) {
                continue;
            }

            if (markAsSaved) {
                // 저장 버튼 요청에는 추천 화면에서 이미 조회한 실제 경로값이
                // 포함되므로 기존 이력 상세도 함께 최신 상태로 바꾼다.
                return refreshExistingHistoryCourse(
                        candidate,
                        request,
                        validated,
                        true
                );
            }

            if (Boolean.TRUE.equals(request.getRouteDetailsResolved())) {
                // 수정 전 응답에는 courseId가 없으므로 장소 구성이 같은 경우에
                // 한해 실제 경로 조회 완료 스냅샷을 기존 이력에 복구한다.
                return refreshExistingHistoryCourse(
                        candidate,
                        request,
                        validated,
                        false
                );
            }

            return toExistingSaveResponse(
                    candidate,
                    validated.transportMode(),
                    details
            );
        }

        return null;
    }

    /** 기존 추천 이력의 기본 합계와 장소별 경로 스냅샷을 한 트랜잭션에서 교체한다. */
    private CourseSaveResponse refreshExistingHistoryCourse(
            TravelCourse course,
            CourseSaveRequest request,
            ValidatedCourse validated,
            boolean markAsSaved
    ) {
        CourseTotals totals = calculateTotals(validated);
        List<CourseDetail> existingDetails = courseDetailRepository
                .findByCourseIdOrderByDayNoAscPlaceOrderAsc(
                        course.getCourseId()
                );

        course.refreshRecommendationSnapshot(
                validated.title(),
                trimToNull(request.getDescription()),
                validated.travelCode(),
                trimToNull(request.getRegion()),
                totals.distanceKm(),
                totals.travelMinutes(),
                totals.visitMinutes(),
                totals.courseMinutes(),
                markAsSaved
        );

        if (existingDetails != null && !existingDetails.isEmpty()) {
            courseDetailRepository.deleteAllInBatch(existingDetails);
        }

        List<CourseDetail> updatedDetails = buildDetails(
                course.getCourseId(),
                validated
        );
        courseDetailRepository.saveAll(updatedDetails);

        return toSaveResponse(
                course,
                validated,
                updatedDetails,
                totals
        );
    }

    /** 저장 요청의 장소별 값을 DB 합계 자릿수에 맞춰 계산한다. */
    private CourseTotals calculateTotals(ValidatedCourse validated) {
        double totalDistanceKm = validated.places().stream()
                .mapToDouble(CourseSavePlaceDto::getDistanceFromPreviousKm)
                .sum();
        double totalTravelTimeMinutes = validated.places().stream()
                .mapToDouble(CourseSavePlaceDto::getTravelTimeFromPreviousMinutes)
                .sum();
        int totalVisitTimeMinutes = validated.places().stream()
                .mapToInt(this::normalizedStayMinutes)
                .sum();

        return new CourseTotals(
                round(totalDistanceKm, 3),
                round(totalTravelTimeMinutes, 2),
                totalVisitTimeMinutes,
                round(totalVisitTimeMinutes + totalTravelTimeMinutes, 2)
        );
    }

    /** 추천 장소 스냅샷을 COURSE_DETAILS 엔티티로 변환한다. */
    private List<CourseDetail> buildDetails(
            Long courseId,
            ValidatedCourse validated
    ) {
        boolean themeCourse = "THEME".equals(validated.courseType());
        List<CourseSavePlaceDto> places = validated.places();
        Map<LocalDate, Integer> dayNumbers = themeCourse
                ? Map.of()
                : createDayNumbers(places);

        // 거리·시간·경로 종류는 해당 장소로 들어오는 이전 구간 값이다.
        return places.stream()
                .map(place -> CourseDetail.builder()
                        .courseId(courseId)
                        .placeId(place.getPlaceId())
                        .dayNo(themeCourse
                                ? place.getDayNo()
                                : dayNumbers.get(place.getVisitDate()))
                        .placeOrder(place.getVisitOrder())
                        .visitTime(trimToNull(place.getVisitTime()))
                        .stayMinutes(normalizedStayMinutes(place))
                        .visitDate(themeCourse ? null : place.getVisitDate())
                        .distanceFromPreviousKm(round(
                                place.getDistanceFromPreviousKm(),
                                3
                        ))
                        .travelTimeFromPreviousMinutes(round(
                                place.getTravelTimeFromPreviousMinutes(),
                                2
                        ))
                        .transitPathType(place.getTransitPathType())
                        .routeEstimated(Boolean.TRUE.equals(
                                place.getRouteEstimated()
                        ))
                        .build())
                .toList();
    }

    private CourseSaveResponse toSaveResponse(
            TravelCourse course,
            ValidatedCourse validated,
            List<CourseDetail> details,
            CourseTotals totals
    ) {
        return CourseSaveResponse.builder()
                .courseId(course.getCourseId())
                .title(course.getTitle())
                .transportMode(validated.transportMode())
                .placeCount(details.size())
                .dayCount("THEME".equals(validated.courseType())
                        ? countThemeDays(validated.places())
                        : createDayNumbers(validated.places()).size())
                .totalDistanceKm(totals.distanceKm())
                .totalTravelTimeMinutes(totals.travelMinutes())
                .totalVisitTimeMinutes(totals.visitMinutes())
                .totalCourseTimeMinutes(totals.courseMinutes())
                .build();
    }

    private CourseSaveResponse toExistingSaveResponse(
            TravelCourse course,
            TransportMode transportMode,
            List<CourseDetail> details
    ) {
        return CourseSaveResponse.builder()
                .courseId(course.getCourseId())
                .title(course.getTitle())
                .transportMode(transportMode)
                .placeCount(details.size())
                .dayCount((int) details.stream()
                        .map(CourseDetail::getVisitDate)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .count())
                .totalDistanceKm(course.getTotalDistanceKm())
                .totalTravelTimeMinutes(course.getTotalTravelTimeMinutes())
                .totalVisitTimeMinutes(course.getTotalVisitTimeMinutes())
                .totalCourseTimeMinutes(course.getTotalCourseTimeMinutes())
                .build();
    }

    /** 추천 옵션의 날짜·장소 구성을 방문 순서와 무관한 안정적인 키로 만든다. */
    private String createCompositionKey(List<CourseSavePlaceDto> places) {
        return places.stream()
                .sorted(Comparator
                        .comparing(CourseSavePlaceDto::getVisitDate)
                        .thenComparing(CourseSavePlaceDto::getPlaceId))
                .map(place -> place.getVisitDate()
                        + ":" + place.getPlaceId())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    /** DB에 저장된 상세 행을 요청과 같은 날짜·장소 구성 키로 변환한다. */
    private String createCompositionKeyFromDetails(List<CourseDetail> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }

        return details.stream()
                .filter(detail -> detail.getVisitDate() != null)
                .filter(detail -> detail.getPlaceId() != null)
                .sorted(Comparator
                        .comparing(CourseDetail::getVisitDate)
                        .thenComparing(CourseDetail::getPlaceId))
                .map(detail -> detail.getVisitDate()
                        + ":" + detail.getPlaceId())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    /** 배치 요청의 null·빈 목록·최대 선택 개수를 검증한다. */
    private List<CourseSaveRequest> validateBatchRequest(
            CourseBatchSaveRequest batchRequest
    ) {
        if (batchRequest == null) {
            throw new IllegalArgumentException("복수 코스 저장 요청은 null일 수 없습니다.");
        }

        List<CourseSaveRequest> courses = batchRequest.getCourses();
        if (courses == null || courses.isEmpty()) {
            throw new IllegalArgumentException("저장할 코스를 한 개 이상 선택해야 합니다.");
        }
        if (courses.size() > MAX_BATCH_COURSE_COUNT) {
            throw new IllegalArgumentException("한 번에 저장할 수 있는 코스는 최대 3개입니다.");
        }
        return List.copyOf(courses);
    }

    /** 한 추천의 복수 옵션이므로 회원 ID와 이동수단이 섞이지 않도록 검증한다. */
    private void validateSameMemberAndTransportMode(List<CourseSaveRequest> requests) {
        Long memberId = requests.get(0).getMemberId();
        TransportMode transportMode = requests.get(0).getTransportMode();
        for (CourseSaveRequest request : requests) {
            if (!memberId.equals(request.getMemberId())) {
                throw new IllegalArgumentException(
                        "복수 저장 요청의 모든 코스는 같은 회원 ID여야 합니다."
                );
            }
            if (transportMode != request.getTransportMode()) {
                throw new IllegalArgumentException(
                        "한 추천에서 저장하는 코스는 모두 같은 이동수단이어야 합니다."
                );
            }
        }
    }

    /** 요청 전체를 검증하고 문자열·코스 유형·장소 정렬을 저장 가능한 형태로 정규화한다. */
    private ValidatedCourse validateAndNormalize(CourseSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("코스 저장 요청은 null일 수 없습니다.");
        }
        validateOptionalPositiveId(request.getCourseId(), "코스 ID");
        validatePositiveId(request.getMemberId(), "회원 ID");
        validateOptionalPositiveId(request.getResultId(), "설문 결과 ID");
        validateOptionalPositiveId(request.getPaymentId(), "결제 ID");

        String title = requireText(request.getTitle(), "코스 제목");
        if (title.length() > 200) {
            throw new IllegalArgumentException("코스 제목은 200자를 초과할 수 없습니다.");
        }

        String courseType = normalizeCourseType(request.getCourseType());
        if ("SURVEY".equals(courseType) && request.getResultId() == null) {
            throw new IllegalArgumentException(
                    "설문 추천 코스는 설문 결과 ID가 필요합니다."
            );
        }

        String sourceCourseKey =
                trimToNull(request.getSourceCourseKey());

        if ("THEME".equals(courseType)
                && sourceCourseKey == null) {
            throw new IllegalArgumentException(
                    "테마 코스는 원본 코스 키가 필요합니다."
            );
        }

        if (!"THEME".equals(courseType)
                && sourceCourseKey != null) {
            throw new IllegalArgumentException(
                    "원본 코스 키는 테마 코스에만 사용할 수 있습니다."
            );
        }

        if (sourceCourseKey != null
                && sourceCourseKey.length() > 50) {
            throw new IllegalArgumentException(
                    "원본 코스 키는 50자를 초과할 수 없습니다."
            );
        }
        String travelCode = normalizeTravelCode(request.getTravelCode());
        TransportMode transportMode = requireTransportMode(
                request.getTransportMode()
        );

        List<CourseSavePlaceDto> places = request.getPlaces();
        if (places == null || places.isEmpty()) {
            throw new IllegalArgumentException("저장할 코스 장소가 한 개 이상 필요합니다.");
        }

        List<CourseSavePlaceDto> sortedPlaces = new ArrayList<>(places);
        Map<Long, CourseSavePlaceDto> firstPlaceById = new LinkedHashMap<>();
        boolean themeCourse = "THEME".equals(courseType);
        Map<Long, Set<Object>> scheduleDaysByPlaceId = new LinkedHashMap<>();
        for (CourseSavePlaceDto place : sortedPlaces) {
            validatePlace(place, themeCourse);
            CourseSavePlaceDto previous =
                    firstPlaceById.putIfAbsent(place.getPlaceId(), place);
            Set<Object> usedDays = scheduleDaysByPlaceId.computeIfAbsent(
                    place.getPlaceId(),
                    ignored -> new HashSet<>()
            );
            boolean newScheduleDay = usedDays.add(scheduleDayKey(
                    place,
                    themeCourse
            ));
            if (previous != null
                    && (!newScheduleDay
                    || !isRepeatedHotelOnAnotherDay(
                            previous,
                            place,
                            themeCourse
                    ))) {
                throw new IllegalArgumentException(
                        "동일한 장소를 코스에 중복 저장할 수 없습니다. placeId="
                                + place.getPlaceId()
                );
            }
        }
        if (themeCourse) {
            sortedPlaces.sort(Comparator
                    .comparing(CourseSavePlaceDto::getDayNo)
                    .thenComparing(CourseSavePlaceDto::getVisitOrder));
            validateSequentialThemeDays(sortedPlaces);
        } else {
            sortedPlaces.sort(Comparator
                    .comparing(CourseSavePlaceDto::getVisitDate)
                    .thenComparing(CourseSavePlaceDto::getVisitOrder));
        }
        validateSequentialOrders(sortedPlaces, themeCourse);
        validateTransitPathTypes(sortedPlaces, transportMode);

        return new ValidatedCourse(
                title,
                courseType,
                travelCode,
                transportMode,
                sortedPlaces
        );
    }

    /**
     * 2일 이상 일정의 같은 숙소만 서로 다른 날짜에 반복 저장할 수 있도록 예외 처리한다.
     * 같은 날짜 중복 또는 HOTEL이 아닌 일반 장소 중복은 기존처럼 거부한다.
     */
    private boolean isRepeatedHotelOnAnotherDay(
            CourseSavePlaceDto previous,
            CourseSavePlaceDto current,
            boolean themeCourse
    ) {
        return isHotel(previous.getCategory())
                && isHotel(current.getCategory())
                && !scheduleDayKey(previous, themeCourse).equals(
                        scheduleDayKey(current, themeCourse)
                );
    }

    private Object scheduleDayKey(
            CourseSavePlaceDto place,
            boolean themeCourse
    ) {
        return themeCourse ? place.getDayNo() : place.getVisitDate();
    }

    private boolean isHotel(String category) {
        if (category == null || category.isBlank()) {
            return false;
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("HOTEL")
                || normalized.equals("숙소")
                || normalized.equals("호텔")
                || normalized.equals("ACCOMMODATION")
                || normalized.equals("LODGING");
    }

    /** 숙소는 DAY의 마지막 도착 지점이므로 전달값과 관계없이 체류시간을 저장하지 않는다. */
    private int normalizedStayMinutes(CourseSavePlaceDto place) {
        return isHotel(place.getCategory())
                ? 0
                : place.getExpectedVisitMinutes();
    }

    /** 상세 장소 한 건의 필수값과 음수가 될 수 없는 계산값을 검증한다. */
    private void validatePlace(
            CourseSavePlaceDto place,
            boolean themeCourse
    ) {
        if (place == null) {
            throw new IllegalArgumentException("저장할 장소는 null일 수 없습니다.");
        }
        validatePositiveId(place.getPlaceId(), "장소 ID");
        if (themeCourse
                && (place.getDayNo() == null || place.getDayNo() < 1)) {
            throw new IllegalArgumentException(
                    "테마 코스 장소의 dayNo는 1 이상이어야 합니다."
            );
        }
        if (!themeCourse && place.getVisitDate() == null) {
            throw new IllegalArgumentException("장소 방문 날짜는 필수입니다.");
        }
        if (place.getVisitOrder() == null || place.getVisitOrder() < 1) {
            throw new IllegalArgumentException("장소 방문 순서는 1 이상이어야 합니다.");
        }
        String visitTime = trimToNull(place.getVisitTime());
        if (visitTime != null
                && !visitTime.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
            throw new IllegalArgumentException("예상 방문 시각은 HH:mm 형식이어야 합니다.");
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

    /** 각 날짜의 방문 순서가 반드시 1부터 시작해 중간 번호 없이 이어지는지 확인한다. */
    private void validateSequentialOrders(
            List<CourseSavePlaceDto> places,
            boolean themeCourse
    ) {
        Object currentDay = null;
        int expectedOrder = 1;

        for (CourseSavePlaceDto place : places) {
            Object scheduleDay = scheduleDayKey(place, themeCourse);
            if (!scheduleDay.equals(currentDay)) {
                currentDay = scheduleDay;
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

    private void validateSequentialThemeDays(
            List<CourseSavePlaceDto> places
    ) {
        int expectedDayNo = 1;
        Integer previousDayNo = null;
        for (CourseSavePlaceDto place : places) {
            if (place.getDayNo().equals(previousDayNo)) {
                continue;
            }
            if (!place.getDayNo().equals(expectedDayNo)) {
                throw new IllegalArgumentException(
                        "테마 코스 dayNo는 1부터 빈 번호 없이 이어져야 합니다."
                );
            }
            previousDayNo = place.getDayNo();
            expectedDayNo++;
        }
    }

    /**
     * DAY 2 이후 숙소 출발 구간은 첫 일반 장소에 귀속해 저장할 수 있게 하고,
     * 그 외 첫 장소와 도보·자동차 코스에는 경로 종류가 잘못 저장되지 않게 한다.
     */
    private void validateTransitPathTypes(
            List<CourseSavePlaceDto> places,
            TransportMode transportMode
    ) {
        boolean themeCourse = places.stream()
                .allMatch(place -> place.getVisitDate() == null);
        Set<LocalDate> datesStartingFromPreviousHotel = themeCourse
                ? Set.of()
                : findDatesStartingFromPreviousHotel(places);

        for (CourseSavePlaceDto place : places) {
            TransitPathType transitPathType = place.getTransitPathType();
            boolean firstPlace = place.getVisitOrder() == 1;
            boolean startsFromPreviousHotel = !themeCourse
                    && datesStartingFromPreviousHotel.contains(
                            place.getVisitDate()
                    );

            if (firstPlace
                    && transitPathType != null
                    && !startsFromPreviousHotel) {
                throw new IllegalArgumentException(
                    "날짜별 첫 장소에는 대중교통 경로 종류를 저장할 수 없습니다."
                );
            }
            if (firstPlace
                    && Boolean.TRUE.equals(place.getRouteEstimated())
                    && !startsFromPreviousHotel) {
                throw new IllegalArgumentException(
                        "날짜별 첫 장소에는 예상 이동 구간을 표시할 수 없습니다."
                );
            }
            if (transportMode != TransportMode.PUBLIC_TRANSIT
                    && transitPathType != null) {
                throw new IllegalArgumentException(
                        "대중교통 경로 종류는 PUBLIC_TRANSIT 코스에서만 사용할 수 있습니다."
                );
            }
        }
    }

    /**
     * 전날 마지막 장소가 숙소인 날짜를 찾는다.
     *
     * <p>추천 화면은 이 숙소를 DAY 2 이후의 별도 출발점으로 표시하므로,
     * 해당 날짜의 첫 일반 장소에는 숙소에서 이동해 온 경로값이 존재할 수 있다.</p>
     */
    private Set<LocalDate> findDatesStartingFromPreviousHotel(
            List<CourseSavePlaceDto> places
    ) {
        Map<LocalDate, List<CourseSavePlaceDto>> placesByDate =
                new LinkedHashMap<>();
        for (CourseSavePlaceDto place : places) {
            placesByDate.computeIfAbsent(
                    place.getVisitDate(),
                    ignored -> new ArrayList<>()
            ).add(place);
        }

        List<LocalDate> visitDates = new ArrayList<>(placesByDate.keySet());
        Set<LocalDate> datesStartingFromHotel = new HashSet<>();
        for (int index = 1; index < visitDates.size(); index++) {
            List<CourseSavePlaceDto> previousDayPlaces =
                    placesByDate.get(visitDates.get(index - 1));
            CourseSavePlaceDto previousDayLastPlace =
                    previousDayPlaces.stream()
                            .max(Comparator.comparing(
                                    CourseSavePlaceDto::getVisitOrder
                            ))
                            .orElse(null);
            if (previousDayLastPlace != null
                    && isHotel(previousDayLastPlace.getCategory())) {
                datesStartingFromHotel.add(visitDates.get(index));
            }
        }
        return datesStartingFromHotel;
    }

    /** 정렬된 방문 날짜를 처음 나타난 순서대로 1일차, 2일차 번호에 매핑한다. */
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

    private int countThemeDays(List<CourseSavePlaceDto> places) {
        return (int) places.stream()
                .map(CourseSavePlaceDto::getDayNo)
                .distinct()
                .count();
    }

    /** 코스 유형을 대문자로 통일하고 허용된 생성 출처인지 확인한다. */
    private String normalizeCourseType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "CUSTOM";
        }

        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_COURSE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "코스 유형은 CUSTOM, SURVEY, CHATBOT, THEME 중 하나여야 합니다."
            );
        }
        return normalized;
    }

    /** 5자리 여행 유형 코드를 대문자로 통일하며 선택값이면 null을 유지한다. */
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

    /** 저장 후 조회에서 이동수단이 사라지지 않도록 필수값으로 검증한다. */
    private TransportMode requireTransportMode(TransportMode transportMode) {
        if (transportMode == null) {
            throw new IllegalArgumentException("이동수단은 필수입니다.");
        }
        return transportMode;
    }

    /** 필수 외래키·식별자가 양의 정수인지 확인한다. */
    private void validatePositiveId(Long value, String fieldName) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(fieldName + "는 1 이상이어야 합니다.");
        }
    }

    /** 선택 식별자는 값이 전달된 경우에만 양의 정수 검증을 적용한다. */
    private void validateOptionalPositiveId(Long value, String fieldName) {
        if (value != null) {
            validatePositiveId(value, fieldName);
        }
    }

    /** 거리와 이동시간이 null·무한대·음수가 아닌지 확인한다. */
    private void validateNonNegativeFinite(Double value, String fieldName) {
        if (value == null || !Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + "은 0 이상의 유한한 숫자여야 합니다.");
        }
    }

    /** 필수 문자열의 앞뒤 공백을 제거하고 빈 문자열을 거부한다. */
    private String requireText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return normalized;
    }

    /** 선택 문자열의 앞뒤 공백을 제거하고 내용이 없으면 null로 통일한다. */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** DB에 저장할 거리·시간 값을 HALF_UP 규칙으로 반올림한다. */
    private double round(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record ValidatedCourse(
            String title,
            String courseType,
            String travelCode,
            TransportMode transportMode,
            List<CourseSavePlaceDto> places
    ) {
    }

    private record CourseTotals(
            double distanceKm,
            double travelMinutes,
            int visitMinutes,
            double courseMinutes
    ) {
    }
}
