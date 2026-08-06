package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDetailResponse;
import com.seoulink.backend.domain.course.dto.response.CourseDayResponse;
import com.seoulink.backend.domain.course.dto.response.CoursePlaceResponse;
import com.seoulink.backend.domain.course.dto.response.CourseRecommendationResponse;
import com.seoulink.backend.domain.course.dto.response.ThemeCourseBookmarkResponse;
import com.seoulink.backend.domain.course.dto.response.ThemeCoursePopularityResponse;
import com.seoulink.backend.domain.course.dto.request.CourseUpdateRequest;
import com.seoulink.backend.domain.course.entity.CourseDetail;
import com.seoulink.backend.domain.course.entity.TravelCourse;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.repository.CourseDetailRepository;
import com.seoulink.backend.domain.course.repository.TravelCourseRepository;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.exception.InvalidTravelCodeException;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import com.seoulink.backend.domain.place.service.PlaceRecommendationService;
import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import com.seoulink.backend.domain.survey.repository.SurveyResultRepository;
import com.seoulink.backend.domain.survey.repository.TravelSurveyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;

/**
 * 저장된 여행 코스와 날짜별 장소 순서를 조회한다.
 */
@Service
public class CourseService {

    private final TravelCourseRepository travelCourseRepository;
    private final CourseDetailRepository courseDetailRepository;
    private final PlaceRepository placeRepository;
    private final SurveyResultRepository surveyResultRepository;
    private final TravelSurveyRepository travelSurveyRepository;
    private final PlaceRecommendationService placeRecommendationService;

    public CourseService(
            TravelCourseRepository travelCourseRepository,
            CourseDetailRepository courseDetailRepository,
            PlaceRepository placeRepository,
            SurveyResultRepository surveyResultRepository,
            TravelSurveyRepository travelSurveyRepository,
            PlaceRecommendationService placeRecommendationService
    ) {
        this.travelCourseRepository = travelCourseRepository;
        this.courseDetailRepository = courseDetailRepository;
        this.placeRepository = placeRepository;
        this.surveyResultRepository = surveyResultRepository;
        this.travelSurveyRepository = travelSurveyRepository;
        this.placeRecommendationService = placeRecommendationService;
    }

    /**
     * 코스 기본정보와 날짜·방문 순서대로 정렬된 장소를 함께 반환한다.
     *
     * <p>COURSE_DETAILS의 placeId를 모아 PLACES를 한 번에 조회하고 장소명·카테고리·
     * 주소·이미지·좌표·테마를 함께 채운다.</p>
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
        Map<Long, Place> placesById = loadPlacesById(details);
        Map<Long, Double> recommendationScores =
                resolveRecommendationScores(course, details);
        List<CourseDayResponse> days = toDayResponses(
                details,
                placesById,
                recommendationScores
        );
        List<String> coverImageUrls = findCoverImages(details, placesById);
        int excludedHotelStayMinutes = excludedHotelStayMinutes(
                details,
                placesById
        );

        return CourseDetailResponse.builder()
                .courseId(course.getCourseId())
                .resultId(course.getResultId())
                .title(course.getTitle())
                .description(course.getDescription())
                .coverImageUrl(firstImage(coverImageUrls))
                .coverImageUrls(coverImageUrls)
                .travelCode(course.getTravelCode())
                .transportMode(resolveTransportMode(course))
                .courseType(course.getCourseType())
                .region(course.getRegion())
                .publicCourse("Y".equalsIgnoreCase(course.getPublicStatus()))
                .viewCount(course.getViewCount())
                .placeCount(details.size())
                .dayCount(days.size())
                .totalDistanceKm(course.getTotalDistanceKm())
                .totalTravelTimeMinutes(course.getTotalTravelTimeMinutes())
                .totalVisitTimeMinutes(Math.max(
                        0,
                        valueOrZero(course.getTotalVisitTimeMinutes())
                                - excludedHotelStayMinutes
                ))
                .totalCourseTimeMinutes(Math.max(
                        0.0,
                        valueOrZero(course.getTotalCourseTimeMinutes())
                                - excludedHotelStayMinutes
                ))
                .estimatedTravelTimes(details.stream().anyMatch(detail ->
                        Boolean.TRUE.equals(detail.getRouteEstimated())
                ))
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

    /** 원본 테마 코스별 회원 저장 횟수를 인기순으로 반환한다. */
    @Transactional(readOnly = true)
    public List<ThemeCoursePopularityResponse> getThemeCoursePopularity() {
        return travelCourseRepository.findThemeCoursePopularity()
                .stream()
                .map(popularity -> new ThemeCoursePopularityResponse(
                        popularity.getSourceCourseKey(),
                        popularity.getSaveCount()
                ))
                .toList();
    }

    /** 회원이 실제로 저장한 코스만 유형과 관계없이 최신순으로 반환한다. */
    @Transactional(readOnly = true)
    public List<CourseRecommendationResponse> getMemberCourses(Long memberId) {
        validateMemberId(memberId);
        return travelCourseRepository
                .findByMemberIdAndSavedStatusOrderByCreatedAtDesc(
                        memberId,
                        "Y"
                )
                .stream()
                .map(this::toRecommendationResponse)
                .toList();
    }

    /**
     * 회원이 특정 테마 코스를 저장했는지 확인한다.
     *
     * <p>저장된 THEME 코스가 있으면 saved=true와 저장된 courseId를 반환하고,
     * 저장되지 않았다면 saved=false와 courseId=null을 반환한다.</p>
     */
    @Transactional(readOnly = true)
    public ThemeCourseBookmarkResponse getThemeCourseBookmarkStatus(
            Long memberId,
            String sourceCourseKey
    ) {
        validateMemberId(memberId);

        String normalizedSourceCourseKey = validateSourceCourseKey(sourceCourseKey);

        return travelCourseRepository
                .findByMemberIdAndSourceCourseKey(memberId, normalizedSourceCourseKey)
                // THEME 코스이면서 실제 저장 상태인 행만 북마크로 인정한다.
                .filter(course ->
                        "THEME".equalsIgnoreCase(course.getCourseType())
                                && course.isSaved()
                )
                .map(course ->
                        ThemeCourseBookmarkResponse.builder()
                                .saved(true)
                                .courseId(course.getCourseId())
                                .sourceCourseKey(normalizedSourceCourseKey)
                                .build()
                )
                .orElseGet(() ->
                        ThemeCourseBookmarkResponse.builder()
                                .saved(false)
                                .courseId(null)
                                .sourceCourseKey(normalizedSourceCourseKey)
                                .build()
                );
    }

    @Transactional(readOnly = true)
    public List<CourseRecommendationResponse> getMemberCoursesByType(
            Long memberId,
            String courseType
    ) {
        validateMemberId(memberId);

        return travelCourseRepository
                .findByMemberIdAndCourseTypeOrderByCreatedAtDesc(
                        memberId,
                        courseType
                )
                .stream()
                .map(this::toRecommendationResponse)
                .toList();
    }

    @Transactional
    public CourseRecommendationResponse updateMemberCustomCourse(
            Long courseId,
            Long memberId,
            CourseUpdateRequest request
    ) {
        TravelCourse course =
                getOwnedCourse(courseId, memberId, "CUSTOM");

        String title =
                request.getTitle() == null
                        ? ""
                        : request.getTitle().trim();

        if (title.isBlank()) {
            throw new IllegalArgumentException(
                    "코스 제목은 필수입니다."
            );
        }

        String publicStatus =
                "Y".equalsIgnoreCase(request.getIsPublic())
                        ? "Y"
                        : "N";

        course.updateBasicInfo(
                title,
                request.getDescription(),
                request.getRegion(),
                publicStatus
        );

        return toRecommendationResponse(course);
    }

    /**
     * 저장한 추천 코스에서 한 건을 해제한다.
     * SURVEY는 추천 이력 보존을 위해 IS_SAVED만 N으로 바꾸고,
     * THEME는 별도 추천 이력이 없으므로 해당 저장 행을 삭제한다.
     */
    @Transactional
    public void removeSavedRecommendedCourse(
            Long courseId,
            Long memberId
    ) {
        validateCourseId(courseId);
        validateMemberId(memberId);

        TravelCourse course = travelCourseRepository
                .findByCourseIdAndMemberId(courseId, memberId)
                .orElseThrow(() -> courseNotFound(courseId));

        if (!course.isSaved()) {
            throw new IllegalArgumentException("이미 저장 해제된 코스입니다.");
        }

        if ("SURVEY".equalsIgnoreCase(course.getCourseType())
                || "THEME".equalsIgnoreCase(course.getCourseType())) {
            course.markUnsaved();
            return;
        }

        throw new IllegalArgumentException("저장한 추천 코스 유형이 아닙니다.");
    }

    @Transactional
    public void deleteMemberCourse(
            Long courseId,
            Long memberId,
            String courseType
    ) {
        TravelCourse course =
                getOwnedCourse(
                        courseId,
                        memberId,
                        courseType
                );

        courseDetailRepository.deleteByCourseId(
                course.getCourseId()
        );

        travelCourseRepository.delete(course);
    }

    private TravelCourse getOwnedCourse(
            Long courseId,
            Long memberId,
            String expectedType
    ) {
        validateCourseId(courseId);
        validateMemberId(memberId);

        TravelCourse course =
                travelCourseRepository
                        .findByCourseIdAndMemberId(
                                courseId,
                                memberId
                        )
                        .orElseThrow(
                                () -> courseNotFound(courseId)
                        );

        if (!expectedType.equalsIgnoreCase(
                course.getCourseType()
        )) {
            throw new IllegalArgumentException(
                    "요청한 유형의 코스가 아닙니다."
            );
        }

        return course;
    }

    /** 저장 엔티티를 추천·내 코스 목록 카드에서 사용하는 요약 응답으로 변환한다. */
    private CourseRecommendationResponse toRecommendationResponse(
            TravelCourse course
    ) {
        List<CourseDetail> details =
                courseDetailRepository.findByCourseIdOrderByDayNoAscPlaceOrderAsc(
                        course.getCourseId()
                );
        Map<Long, Place> placesById = loadPlacesById(details);
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
        TransportMode transportMode = resolveTransportMode(course);
        List<String> coverImageUrls = findCoverImages(details, placesById);

        return CourseRecommendationResponse.builder()
                .courseId(course.getCourseId())
                .resultId(course.getResultId())
                .recommendationKey(createRecommendationKey(
                        details,
                        transportMode
                ))
                .title(course.getTitle())
                .description(course.getDescription())
                .coverImageUrl(firstImage(coverImageUrls))
                .coverImageUrls(coverImageUrls)
                .courseType(course.getCourseType())
                .sourceCourseKey(course.getSourceCourseKey())
                .transportMode(transportMode)
                .regions(regions)
                .tags(createThemeTags(placesById.values()))
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

    /**
     * 추천 결과의 recommendationKey와 같은 규칙으로 저장 코스의 구성 키를 만든다.
     *
     * <p>방문 순서가 달라도 날짜별 장소 구성이 같으면 같은 추천 코스로
     * 판별할 수 있도록 날짜와 장소 ID로 정렬한다.</p>
     */
    private String createRecommendationKey(
            List<CourseDetail> details,
            TransportMode transportMode
    ) {
        if (transportMode == null || details == null || details.isEmpty()) {
            return null;
        }

        String composition = details.stream()
                .filter(detail -> detail.getVisitDate() != null)
                .filter(detail -> detail.getPlaceId() != null)
                .sorted(Comparator
                        .comparing(CourseDetail::getVisitDate)
                        .thenComparing(CourseDetail::getPlaceId))
                .map(detail -> detail.getVisitDate()
                        + ":" + detail.getPlaceId())
                .reduce((left, right) -> left + "," + right)
                .orElse("");

        return composition.isBlank()
                ? null
                : transportMode.name() + ":" + composition;
    }

    /** 로그인 연동 전 쿼리 파라미터로 받은 회원 ID의 최소 형식을 검증한다. */
    private void validateMemberId(Long memberId) {
        if (memberId == null || memberId < 1) {
            throw new IllegalArgumentException("회원 ID는 1 이상이어야 합니다.");
        }
    }

    /** 원본 테마 코스 키를 검사하고 앞뒤 공백을 제거한 값을 반환한다. */
    private String validateSourceCourseKey(
            String sourceCourseKey
    ) {
        if (sourceCourseKey == null
                || sourceCourseKey.isBlank()) {
            throw new IllegalArgumentException("원본 테마 코스 키가 필요합니다.");
        }

        String normalizedSourceCourseKey = sourceCourseKey.trim();

        if (normalizedSourceCourseKey.length() > 50) {
            throw new IllegalArgumentException("원본 테마 코스 키는 50자를 초과할 수 없습니다.");
        }
        return normalizedSourceCourseKey;
    }

    /** 회원이 저장한 테마 코스 북마크를 해제한다. */
    @Transactional
    public void deleteThemeCourseBookmark(
            Long memberId,
            String sourceCourseKey
    ) {
        validateMemberId(memberId);

        String normalizedSourceCourseKey = validateSourceCourseKey(sourceCourseKey);

        TravelCourse course = travelCourseRepository
                .findByMemberIdAndSourceCourseKey(memberId, normalizedSourceCourseKey)
                .filter(savedCourse ->
                        "THEME".equalsIgnoreCase(
                                savedCourse.getCourseType()
                        )
                )
                .orElseThrow(() -> new IllegalArgumentException("저장된 테마 코스를 찾을 수 없습니다."));

        if (!course.isSaved()) {
            return;
        }

        // 후기 등 다른 데이터가 코스를 참조할 수 있으므로 행을 삭제하지 않고
        // 저장 상태만 해제한다.
        course.markUnsaved();
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
    private List<CourseDayResponse> toDayResponses(
            List<CourseDetail> details,
            Map<Long, Place> placesById,
            Map<Long, Double> recommendationScores
    ) {
        Map<Integer, List<CourseDetail>> detailsByDay = new TreeMap<>();
        for (CourseDetail detail : details) {
            detailsByDay
                    .computeIfAbsent(detail.getDayNo(), ignored -> new ArrayList<>())
                    .add(detail);
        }

        List<CourseDayResponse> days = new ArrayList<>();
        CourseDetail previousDayHotel = null;

        for (Map.Entry<Integer, List<CourseDetail>> entry
                : detailsByDay.entrySet()) {
            List<CourseDetail> dailyDetails = entry.getValue();
            CoursePlaceResponse routeOriginPlace =
                    shouldRestoreHotelOrigin(previousDayHotel, dailyDetails)
                            ? toRouteOriginResponse(
                                    previousDayHotel,
                                    placesById.get(
                                            previousDayHotel.getPlaceId()
                                    ),
                                    recommendationScores
                            )
                            : null;

            days.add(toDayResponse(
                    entry.getKey(),
                    dailyDetails,
                    placesById,
                    recommendationScores,
                    routeOriginPlace
            ));
            previousDayHotel = findLastHotelDetail(
                    dailyDetails,
                    placesById
            );
        }

        return List.copyOf(days);
    }

    /** 한 날짜의 상세 장소와 거리·시간 합계를 날짜별 응답으로 변환한다. */
    private CourseDayResponse toDayResponse(
            Integer dayNo,
            List<CourseDetail> dailyDetails,
            Map<Long, Place> placesById,
            Map<Long, Double> recommendationScores,
            CoursePlaceResponse routeOriginPlace
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
                .mapToInt(detail -> normalizedStayMinutes(
                        detail,
                        placesById.get(detail.getPlaceId())
                ))
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
                .routeOriginPlace(routeOriginPlace)
                .places(dailyDetails.stream()
                        .map(detail -> toPlaceResponse(
                                detail,
                                placesById.get(detail.getPlaceId()),
                                recommendationScores
                        ))
                        .toList())
                .build();
    }

    /**
     * DAY 2 이후에는 전날 마지막 숙소를 표시 전용 출발점으로 복원한다.
     * 이미 같은 숙소가 해당 날짜 첫 상세 행에 저장된 구버전 데이터는 중복 표시하지 않는다.
     */
    private boolean shouldRestoreHotelOrigin(
            CourseDetail previousDayHotel,
            List<CourseDetail> dailyDetails
    ) {
        if (previousDayHotel == null
                || dailyDetails == null
                || dailyDetails.isEmpty()) {
            return false;
        }
        return !previousDayHotel.getPlaceId().equals(
                dailyDetails.get(0).getPlaceId()
        );
    }

    /** 하루의 마지막 숙소 상세 행을 다음 날 출발점 후보로 찾는다. */
    private CourseDetail findLastHotelDetail(
            List<CourseDetail> dailyDetails,
            Map<Long, Place> placesById
    ) {
        for (int index = dailyDetails.size() - 1; index >= 0; index--) {
            CourseDetail detail = dailyDetails.get(index);
            if (isHotel(placesById.get(detail.getPlaceId()))) {
                return detail;
            }
        }
        return null;
    }

    /** 전날 숙소를 현재 날짜의 이동값 없는 표시 전용 출발 장소로 변환한다. */
    private CoursePlaceResponse toRouteOriginResponse(
            CourseDetail hotelDetail,
            Place hotel,
            Map<Long, Double> recommendationScores
    ) {
        CoursePlaceResponse response = toPlaceResponse(
                hotelDetail,
                hotel,
                recommendationScores
        );
        response.setVisitOrder(1);
        response.setVisitTime(null);
        response.setExpectedVisitMinutes(0);
        response.setDistanceFromPreviousKm(0.0);
        response.setTravelTimeFromPreviousMinutes(0.0);
        response.setTransitPathType(null);
        response.setRouteEstimated(false);
        return response;
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
     * <p>표시 정보는 같은 상세 조회에서 일괄 조회한 PLACES 행으로 보강한다.</p>
     */
    private CoursePlaceResponse toPlaceResponse(
            CourseDetail detail,
            Place place,
            Map<Long, Double> recommendationScores
    ) {
        // 저장한 이전→현재 구간의 경로 종류를 거리·시간과 함께 상세 화면까지 전달한다.
        CoursePlaceResponse.CoursePlaceResponseBuilder response =
                CoursePlaceResponse.builder()
                .detailId(detail.getDetailId())
                .placeId(detail.getPlaceId())
                .recommendationScore(recommendationScores.get(
                        detail.getPlaceId()
                ))
                .visitOrder(detail.getPlaceOrder())
                .memo(detail.getMemo())
                .visitTime(detail.getVisitTime())
                .expectedVisitMinutes(normalizedStayMinutes(detail, place))
                .distanceFromPreviousKm(detail.getDistanceFromPreviousKm())
                .travelTimeFromPreviousMinutes(
                        detail.getTravelTimeFromPreviousMinutes()
                )
                .transitPathType(detail.getTransitPathType())
                .routeEstimated(Boolean.TRUE.equals(
                        detail.getRouteEstimated()
                ));

        if (place != null) {
            response
                    .placeName(place.getName())
                    .category(place.getCategory())
                    .address(place.getAddress())
                    .roadAddress(place.getRoadAddress())
                    .imageUrl(normalizeImageUrl(place.getImageUrl()))
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .themePalaceCultureYn(place.getThemePalaceCultureYn())
                    .themeNatureHangangYn(place.getThemeNatureHangangYn())
                    .themeDateYn(place.getThemeDateYn())
                    .themeFoodTourYn(place.getThemeFoodTourYn())
                    .themeCafeTourYn(place.getThemeCafeTourYn())
                    .themeShoppingHotplaceYn(place.getThemeShoppingHotplaceYn())
                    .themeNightViewYn(place.getThemeNightViewYn())
                    .themeHotelStayYn(place.getThemeHotelStayYn());
        }

        return response.build();
    }

    /** 숙소는 방문 장소가 아니라 숙박 지점이므로 체류시간 합계에서 제외한다. */
    private int normalizedStayMinutes(
            CourseDetail detail,
            Place place
    ) {
        return isHotel(place)
                ? 0
                : valueOrZero(detail.getStayMinutes());
    }

    private int excludedHotelStayMinutes(
            List<CourseDetail> details,
            Map<Long, Place> placesById
    ) {
        return details.stream()
                .filter(detail -> isHotel(
                        placesById.get(detail.getPlaceId())
                ))
                .mapToInt(detail ->
                        valueOrZero(detail.getStayMinutes())
                )
                .sum();
    }

    private boolean isHotel(Place place) {
        return place != null
                && "HOTEL".equalsIgnoreCase(place.getCategory());
    }

    /** 상세 행의 PLACE_ID를 중복 제거해 PLACES를 한 번에 조회한다. */
    private Map<Long, Place> loadPlacesById(List<CourseDetail> details) {
        Set<Long> placeIds = new LinkedHashSet<>();
        for (CourseDetail detail : details) {
            if (detail.getPlaceId() != null) {
                placeIds.add(detail.getPlaceId());
            }
        }

        if (placeIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Place> placesById = new LinkedHashMap<>();
        for (Place place : placeRepository.findAllById(placeIds)) {
            placesById.put(place.getPlaceId(), place);
        }
        return placesById;
    }

    /**
     * 방문 순서대로 실제 장소 사진 후보를 모두 반환한다.
     *
     * <p>첫 URL이 만료되거나 깨져도 목록과 상세 화면이 다음 실제 장소 사진을
     * 같은 순서로 시도할 수 있도록 한 장만 고르지 않는다.</p>
     */
    private List<String> findCoverImages(
            List<CourseDetail> details,
            Map<Long, Place> placesById
    ) {
        Set<String> imageUrls = new LinkedHashSet<>();
        for (CourseDetail detail : details) {
            Place place = placesById.get(detail.getPlaceId());
            String imageUrl = place == null
                    ? null
                    : normalizeImageUrl(place.getImageUrl());
            if (imageUrl != null) {
                imageUrls.add(imageUrl);
            }
        }
        return List.copyOf(imageUrls);
    }

    private String firstImage(List<String> imageUrls) {
        return imageUrls == null || imageUrls.isEmpty()
                ? null
                : imageUrls.get(0);
    }

    /**
     * 공공데이터에 남아 있는 HTTP 주소를 HTTPS로 통일하고 빈 이미지 값을 제거한다.
     */
    private String normalizeImageUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        if ("null".equalsIgnoreCase(normalized)
                || "undefined".equalsIgnoreCase(normalized)
                || "n/a".equalsIgnoreCase(normalized)) {
            return null;
        }
        if (normalized.regionMatches(true, 0, "http://", 0, 7)) {
            return "https://" + normalized.substring(7);
        }
        return normalized;
    }

    /**
     * 최초 추천과 같은 점수 서비스를 사용해 기존 이력의 장소별 표시 점수도 복원한다.
     */
    private Map<Long, Double> resolveRecommendationScores(
            TravelCourse course,
            List<CourseDetail> details
    ) {
        if (placeRecommendationService == null
                || course == null
                || course.getResultId() == null
                || details == null
                || details.isEmpty()) {
            return Map.of();
        }

        SurveyResult surveyResult = surveyResultRepository
                .findById(course.getResultId())
                .orElse(null);
        String travelCode = course.getTravelCode();
        if ((travelCode == null || travelCode.isBlank())
                && surveyResult != null) {
            travelCode = surveyResult.getTravelCode();
        }
        if (travelCode == null || travelCode.isBlank()) {
            return Map.of();
        }

        String companionType = null;
        if (surveyResult != null && surveyResult.getSurveyId() != null) {
            companionType = travelSurveyRepository
                    .findById(surveyResult.getSurveyId())
                    .map(TravelSurvey::getCompanionType)
                    .orElse(null);
        }

        List<Long> placeIds = details.stream()
                .map(CourseDetail::getPlaceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        try {
            Map<Long, Double> scores = placeRecommendationService
                    .findDisplayScores(
                            travelCode,
                            companionType,
                            placeIds
                    );
            return scores == null ? Map.of() : scores;
        } catch (InvalidTravelCodeException | IllegalArgumentException ignored) {
            // 구버전의 잘못된 코드·동행 표기 때문에 상세 전체가 실패하지 않게 한다.
            return Map.of();
        }
    }

    /** 장소의 8개 테마 Y/N 값을 코스 카드용 태그로 변환한다. */
    private List<String> createThemeTags(Iterable<Place> places) {
        Set<String> tags = new LinkedHashSet<>();
        for (Place place : places) {
            addThemeTag(tags, place.getThemePalaceCultureYn(), "역사·문화");
            addThemeTag(tags, place.getThemeNatureHangangYn(), "자연·한강");
            addThemeTag(tags, place.getThemeDateYn(), "데이트");
            addThemeTag(tags, place.getThemeFoodTourYn(), "맛집탐방");
            addThemeTag(tags, place.getThemeCafeTourYn(), "카페투어");
            addThemeTag(tags, place.getThemeShoppingHotplaceYn(), "쇼핑·핫플");
            addThemeTag(tags, place.getThemeNightViewYn(), "야경");
            addThemeTag(tags, place.getThemeHotelStayYn(), "숙소");
        }
        return tags.stream().limit(4).toList();
    }

    private void addThemeTag(Set<String> tags, String value, String label) {
        if ("Y".equalsIgnoreCase(value)) {
            tags.add(label);
        }
    }

    /**
     * 코스의 RESULT_ID를 통해 SURVEY_RESULT와 TRAVEL_SURVEY를 따라가 기존
     * TRANSPORT_TYPE 값을 코스 API enum으로 복원한다.
     */
    private TransportMode resolveTransportMode(TravelCourse course) {
        if (course.getResultId() == null) {
            return null;
        }

        return surveyResultRepository.findById(course.getResultId())
                .map(SurveyResult::getSurveyId)
                .flatMap(travelSurveyRepository::findById)
                .map(survey -> TransportMode.fromSurveyTransportType(
                        survey.getTransportType()
                ))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CourseRecommendationResponse> getSavedMemberCourses(
            Long memberId
    ) {
        validateMemberId(memberId);

        return travelCourseRepository
                .findByMemberIdAndSavedStatusAndCourseTypeInOrderByCreatedAtDesc(
                        memberId,
                        "Y",
                        List.of("SURVEY", "THEME")
                )
                .stream()
                .map(this::toRecommendationResponse)
                .toList();
    }
}
