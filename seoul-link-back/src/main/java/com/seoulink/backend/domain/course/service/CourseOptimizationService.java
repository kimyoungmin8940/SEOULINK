package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.domain.course.service.DistanceService.RouteMatrix;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 추천 장소 후보를 날짜별로 나누고 실제 이동시간이 짧은 순서로 정렬하는 서비스이다.
 *
 * <p>중복 장소를 한 번만 남긴 뒤 각 날짜에서 추천 점수가 가장 높은 장소를 첫 장소로 선택하고,
 * OpenRouteService 경로 행렬의 이동시간을 기준으로 최근접 이웃 초기 경로를 만든 뒤,
 * 첫 장소를 고정한 2-opt로 총 이동시간과 거리를 한 번 더 줄인다.
 * 한 구간이 선택한 이동수단의 이동시간 중심 허용 기준을 넘으면 같은 날짜·카테고리의
 * 예비 후보 중 기준 안에 들어오는 가장 가까운 장소로 교체한 뒤 전체 경로를 다시 계산한다.
 * 외부 API를 사용할 수 없으면 {@link DistanceService}가 직선거리 방식으로 자동 대체하고,
 * 예상 방문 시간은 {@link VisitDurationService}가 카테고리에 따라 계산한다.</p>
 */
@Service
public class CourseOptimizationService {

    // 외부 API의 부동소수점 오차 때문에 사실상 같은 경로 비용을 다르게 보지 않도록 한다.
    private static final double ROUTE_TIE_EPSILON = 0.000000001;

    private final DistanceService distanceService;
    private final VisitDurationService visitDurationService;

    public CourseOptimizationService(
            DistanceService distanceService,
            VisitDurationService visitDurationService
    ) {
        this.distanceService = distanceService;
        this.visitDurationService = visitDurationService;
    }

    /**
     * 추천 장소 후보를 날짜별 방문 순서로 최적화한다.
     *
     * <p>날짜는 빠른 순서로 처리하며 방문 순서는 날짜마다 1부터 다시 부여한다.
     * 각 날짜의 첫 장소는 이전 장소가 없으므로 거리와 이동시간을 모두 0으로 둔다.</p>
     *
     * @param request 장소 후보 목록을 담은 최적화 요청
     * @return 방문 순서, 이동거리, 이동시간이 포함된 최적화 결과
     */
    public CourseOptimizeResponse optimize(CourseOptimizeRequest request) {
        return optimize(request, Map.of(), true, false);
    }

    /**
     * 추천 옵션별로 날짜마다 다른 출발 장소를 우선 적용해 코스를 최적화한다.
     * 지정한 장소가 해당 날짜 후보에 없으면 기존의 추천 점수 우선 규칙으로 돌아간다.
     */
    CourseOptimizeResponse optimize(
            CourseOptimizeRequest request,
            Map<LocalDate, Long> preferredFirstPlaceIdsByDate
    ) {
        return optimize(
                request,
                preferredFirstPlaceIdsByDate,
                true,
                false
        );
    }

    /**
     * 추천 코스 세 가지를 만드는 동안에는 외부 경로 API를 호출하지 않는다.
     * 카드에 표시되는 DAY의 실제 인접 구간은 별도 요청으로 조회한다.
     */
    CourseOptimizeResponse optimizeForRecommendation(
            CourseOptimizeRequest request,
            Map<LocalDate, Long> preferredFirstPlaceIdsByDate
    ) {
        return optimize(
                request,
                preferredFirstPlaceIdsByDate,
                false,
                true
        );
    }

    /**
     * 일반 장소의 순서 최적화가 끝난 뒤 마지막 여행일을 제외한 날짜 끝에
     * 같은 숙소를 고정 삽입한다.
     *
     * <p>추천 생성 중 외부 경로 API를 호출하지 않는 정책을 유지하기 위해 마지막 장소에서
     * 숙소까지의 구간은 추정 행렬로만 계산한다. 카드에 표시된 DAY를 조회할 때 기존
     * {@code /route-details} 흐름이 이 구간까지 실제 경로로 보정한다.</p>
     */
    CourseOptimizeResponse appendFixedHotelBeforeFinalDayForRecommendation(
            CourseOptimizeResponse optimized,
            PlaceCandidateDto hotelCandidate
    ) {
        if (optimized == null
                || hotelCandidate == null
                || optimized.getOptimizedPlaces() == null
                || optimized.getOptimizedPlaces().isEmpty()) {
            return optimized;
        }

        TransportMode transportMode = optimized.getTransportMode();
        if (transportMode == null) {
            throw new IllegalArgumentException("이동수단은 필수입니다.");
        }

        Map<LocalDate, List<OptimizedPlaceDto>> placesByDate = new TreeMap<>();
        for (OptimizedPlaceDto place : optimized.getOptimizedPlaces()) {
            placesByDate
                    .computeIfAbsent(
                            place.getVisitDate(),
                            ignored -> new ArrayList<>()
                    )
                    .add(place);
        }

        List<OptimizedPlaceDto> withHotels =
                new ArrayList<>(optimized.getOptimizedPlaces());
        boolean appended = false;
        LocalDate finalVisitDate = placesByDate.keySet().stream()
                .max(Comparator.naturalOrder())
                .orElseThrow();

        for (Map.Entry<LocalDate, List<OptimizedPlaceDto>> entry
                : placesByDate.entrySet()) {
            // 마지막 날은 여행 종료일이므로 숙박 장소를 추가하지 않는다.
            if (entry.getKey().equals(finalVisitDate)) {
                continue;
            }
            List<OptimizedPlaceDto> dailyPlaces = entry.getValue();
            boolean alreadyContainsHotel = dailyPlaces.stream()
                    .anyMatch(place -> hotelCandidate.getPlaceId().equals(
                            place.getPlaceId()
                    ));
            if (alreadyContainsHotel) {
                continue;
            }

            OptimizedPlaceDto lastPlace = dailyPlaces.stream()
                    .max(Comparator.comparing(OptimizedPlaceDto::getVisitOrder))
                    .orElseThrow();
            PlaceCandidateDto datedHotel = copyCandidateForDate(
                    hotelCandidate,
                    entry.getKey()
            );
            List<PlaceCandidateDto> legCandidates = List.of(
                    toCandidate(lastPlace),
                    datedHotel
            );
            RouteMatrix routeMatrix =
                    distanceService.calculateCandidatePoolMatrix(
                            legCandidates,
                            transportMode
                    );

            withHotels.add(toOptimizedPlace(
                    datedHotel,
                    lastPlace.getVisitOrder() + 1,
                    routeMatrix.getDistanceKm(0, 1),
                    routeMatrix.getTravelTimeMinutes(0, 1),
                    routeMatrix.getTransitPathType(0, 1),
                    routeMatrix.isEstimated(0, 1)
            ));
            appended = true;
        }

        if (!appended) {
            return optimized;
        }

        withHotels.sort(Comparator
                .comparing(OptimizedPlaceDto::getVisitDate)
                .thenComparing(OptimizedPlaceDto::getVisitOrder));
        double totalDistanceKm = withHotels.stream()
                .mapToDouble(place -> place.getDistanceFromPreviousKm() == null
                        ? 0.0
                        : place.getDistanceFromPreviousKm())
                .sum();
        double totalTravelTimeMinutes = withHotels.stream()
                .mapToDouble(place -> place.getTravelTimeFromPreviousMinutes() == null
                        ? 0.0
                        : place.getTravelTimeFromPreviousMinutes())
                .sum();
        int totalVisitTimeMinutes = withHotels.stream()
                .mapToInt(place -> place.getExpectedVisitMinutes() == null
                        ? 0
                        : place.getExpectedVisitMinutes())
                .sum();
        boolean estimatedTravelTimes = withHotels.stream()
                .anyMatch(place -> Boolean.TRUE.equals(place.getRouteEstimated()));

        return CourseOptimizeResponse.builder()
                .transportMode(transportMode)
                .estimatedTravelTimes(estimatedTravelTimes)
                .optimizedPlaces(withHotels)
                .totalDistanceKm(totalDistanceKm)
                .totalTravelTimeMinutes(totalTravelTimeMinutes)
                .totalVisitTimeMinutes(totalVisitTimeMinutes)
                .totalCourseTimeMinutes(
                        totalVisitTimeMinutes + totalTravelTimeMinutes
                )
                .build();
    }

    private PlaceCandidateDto copyCandidateForDate(
            PlaceCandidateDto source,
            LocalDate visitDate
    ) {
        return PlaceCandidateDto.builder()
                .placeId(source.getPlaceId())
                .placeName(source.getPlaceName())
                .category(source.getCategory())
                .region(source.getRegion())
                .address(source.getAddress())
                .roadAddress(source.getRoadAddress())
                .imageUrl(source.getImageUrl())
                .recommendationScore(source.getRecommendationScore())
                .latitude(source.getLatitude())
                .longitude(source.getLongitude())
                .visitDate(visitDate)
                .themePalaceCultureYn(source.getThemePalaceCultureYn())
                .themeNatureHangangYn(source.getThemeNatureHangangYn())
                .themeDateYn(source.getThemeDateYn())
                .themeFoodTourYn(source.getThemeFoodTourYn())
                .themeCafeTourYn(source.getThemeCafeTourYn())
                .themeShoppingHotplaceYn(source.getThemeShoppingHotplaceYn())
                .themeNightViewYn(source.getThemeNightViewYn())
                .themeHotelStayYn(source.getThemeHotelStayYn())
                .alternativeCandidates(List.of())
                .build();
    }

    private PlaceCandidateDto toCandidate(OptimizedPlaceDto source) {
        return PlaceCandidateDto.builder()
                .placeId(source.getPlaceId())
                .placeName(source.getPlaceName())
                .category(source.getCategory())
                .region(source.getRegion())
                .address(source.getAddress())
                .roadAddress(source.getRoadAddress())
                .imageUrl(source.getImageUrl())
                .recommendationScore(source.getRecommendationScore())
                .latitude(source.getLatitude())
                .longitude(source.getLongitude())
                .visitDate(source.getVisitDate())
                .themePalaceCultureYn(source.getThemePalaceCultureYn())
                .themeNatureHangangYn(source.getThemeNatureHangangYn())
                .themeDateYn(source.getThemeDateYn())
                .themeFoodTourYn(source.getThemeFoodTourYn())
                .themeCafeTourYn(source.getThemeCafeTourYn())
                .themeShoppingHotplaceYn(source.getThemeShoppingHotplaceYn())
                .themeNightViewYn(source.getThemeNightViewYn())
                .themeHotelStayYn(source.getThemeHotelStayYn())
                .alternativeCandidates(List.of())
                .build();
    }

    private CourseOptimizeResponse optimize(
            CourseOptimizeRequest request,
            Map<LocalDate, Long> preferredFirstPlaceIdsByDate,
            boolean resolveActualRouteLegs,
            boolean estimateCandidatePool
    ) {
        if (request == null) {
            throw new IllegalArgumentException("코스 최적화 요청은 null일 수 없습니다.");
        }
        TransportMode transportMode = request.getTransportMode();
        if (transportMode == null) {
            throw new IllegalArgumentException("이동수단은 필수입니다.");
        }

        List<PlaceCandidateDto> candidates = request.getPlaceCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return CourseOptimizeResponse.builder()
                    .transportMode(transportMode)
                    .estimatedTravelTimes(false)
                    .optimizedPlaces(new ArrayList<>())
                    .totalDistanceKm(0.0)
                    .totalTravelTimeMinutes(0.0)
                    .totalVisitTimeMinutes(0)
                    .totalCourseTimeMinutes(0.0)
                    .build();
        }

        List<PlaceCandidateDto> currentCandidates = validateAndRemoveDuplicates(candidates);

        // 최종 JSON은 각 원본 장소 안에 전용 대체 후보를 넣는다.
        // 기존 /optimize 호출부의 최상위 대체 후보도 당분간 호환한다.
        Map<Long, List<PlaceCandidateDto>> alternativesByCurrentPlaceId =
                createNestedAlternativePools(currentCandidates);
        List<PlaceCandidateDto> legacyAlternatives = validateAndRemoveDuplicates(
                request.getAlternativeCandidates()
        );
        int alternativeCount = alternativesByCurrentPlaceId.values().stream()
                .mapToInt(List::size)
                .sum() + legacyAlternatives.size();

        if (alternativeCount == 0) {
            return optimizeCandidates(
                    currentCandidates,
                    transportMode,
                    preferredFirstPlaceIdsByDate,
                    resolveActualRouteLegs,
                    estimateCandidatePool
            );
        }

        // 원래 코스 장소와 이미 소비한 대체 후보가 다시 들어가지 않도록 ID를 추적한다.
        Set<Long> originalPlaceIds = new HashSet<>();
        Set<Long> currentCoursePlaceIds = new HashSet<>();
        for (PlaceCandidateDto candidate : currentCandidates) {
            originalPlaceIds.add(candidate.getPlaceId());
            currentCoursePlaceIds.add(candidate.getPlaceId());
        }
        Set<Long> consumedAlternativeIds = new HashSet<>();

        // 교체 후보 한 건은 최대 한 번만 사용하므로 전체 후보 수만큼 반복하면 종료된다.
        for (int attempt = 0; attempt < alternativeCount; attempt++) {
            CourseOptimizeResponse optimized = optimizeCandidates(
                    currentCandidates,
                    transportMode,
                    preferredFirstPlaceIdsByDate,
                    false,
                    estimateCandidatePool
            );
            Replacement replacement = findReplacement(
                    optimized,
                    currentCandidates,
                    alternativesByCurrentPlaceId,
                    legacyAlternatives,
                    originalPlaceIds,
                    currentCoursePlaceIds,
                    consumedAlternativeIds,
                    transportMode,
                    estimateCandidatePool
            );

            if (replacement == null) {
                return optimizeCandidates(
                        currentCandidates,
                        transportMode,
                        preferredFirstPlaceIdsByDate,
                        resolveActualRouteLegs,
                        estimateCandidatePool
                );
            }

            Long distantPlaceId = replacement.distantPlace().getPlaceId();
            Long alternativePlaceId = replacement.alternativePlace().getPlaceId();
            replaceCandidateByPlaceId(
                    currentCandidates,
                    distantPlaceId,
                    replacement.alternativePlace()
            );

            // 교체 뒤에도 같은 원본 장소의 남은 전용 후보만 계속 검토하도록 풀을 이동한다.
            List<PlaceCandidateDto> candidatePool =
                    alternativesByCurrentPlaceId.remove(distantPlaceId);
            if (candidatePool != null) {
                alternativesByCurrentPlaceId.put(alternativePlaceId, candidatePool);
            }

            currentCoursePlaceIds.remove(distantPlaceId);
            currentCoursePlaceIds.add(alternativePlaceId);
            consumedAlternativeIds.add(alternativePlaceId);
        }

        return optimizeCandidates(
                currentCandidates,
                transportMode,
                preferredFirstPlaceIdsByDate,
                resolveActualRouteLegs,
                estimateCandidatePool
        );
    }

    /**
     * 프런트가 전달한 방문 순서를 바꾸지 않고 화면에 필요한 실제 인접 구간만 조회한다.
     *
     * <p>추천 카드에 한 DAY를 표시할 때 사용한다. 후보 재선정·최근접 경로
     * 계산을 다시 하지 않으므로 ODsay 호출 수는 정확히 {@code 장소 수 - 1} 이하이다.</p>
     */
    public CourseOptimizeResponse resolveFixedRouteDetails(
            CourseOptimizeRequest request
    ) {
        return resolveFixedRoute(request, true);
    }

    /**
     * 큰 후보 풀에서 여러 코스 조합을 비교할 때 방문 순서를 유지한 추정 경로를 만든다.
     * 최종 선택된 코스만 {@link #resolveFixedRouteDetails(CourseOptimizeRequest)}로
     * 실제 인접 구간을 조회한다.
     */
    CourseOptimizeResponse resolveFixedRouteEstimates(
            CourseOptimizeRequest request
    ) {
        return resolveFixedRoute(request, false);
    }

    private CourseOptimizeResponse resolveFixedRoute(
            CourseOptimizeRequest request,
            boolean resolveActualRouteLegs
    ) {
        if (request == null) {
            throw new IllegalArgumentException("경로 상세 요청은 null일 수 없습니다.");
        }
        TransportMode transportMode = request.getTransportMode();
        if (transportMode == null) {
            throw new IllegalArgumentException("이동수단은 필수입니다.");
        }

        List<PlaceCandidateDto> requestedCandidates =
                request.getPlaceCandidates();
        if (requestedCandidates == null || requestedCandidates.isEmpty()) {
            return CourseOptimizeResponse.builder()
                    .transportMode(transportMode)
                    .estimatedTravelTimes(false)
                    .optimizedPlaces(new ArrayList<>())
                    .totalDistanceKm(0.0)
                    .totalTravelTimeMinutes(0.0)
                    .totalVisitTimeMinutes(0)
                    .totalCourseTimeMinutes(0.0)
                    .build();
        }

        // 고정 경로 상세 조회는 이미 선택된 장소와 순서를 그대로 보존해야 한다.
        // 같은 placeId가 다른 날짜에 재사용된 경우도 별개의 방문이므로 제거하지 않는다.
        List<PlaceCandidateDto> candidates = new ArrayList<>(
                requestedCandidates.size()
        );
        for (PlaceCandidateDto candidate : requestedCandidates) {
            validateCandidate(candidate);
            candidates.add(candidate);
        }
        Map<LocalDate, List<PlaceCandidateDto>> candidatesByDate =
                new LinkedHashMap<>();
        for (PlaceCandidateDto candidate : candidates) {
            candidatesByDate
                    .computeIfAbsent(
                            candidate.getVisitDate(),
                            ignored -> new ArrayList<>()
                    )
                    .add(candidate);
        }

        List<OptimizedPlaceDto> optimizedPlaces = new ArrayList<>();
        double totalDistanceKm = 0.0;
        double totalTravelTimeMinutes = 0.0;
        boolean estimatedTravelTimes = false;

        for (List<PlaceCandidateDto> dailyCandidates
                : candidatesByDate.values()) {
            List<Integer> routeIndexes = createIndexes(
                    dailyCandidates.size()
            );
            RouteMatrix routeMatrix = resolveActualRouteLegs
                    ? distanceService.calculateRouteLegMatrix(
                            dailyCandidates,
                            transportMode,
                            routeIndexes
                    )
                    : distanceService.calculateCandidatePoolMatrix(
                            dailyCandidates,
                            transportMode
                    );

            for (int index = 0; index < dailyCandidates.size(); index++) {
                double distanceFromPreviousKm = 0.0;
                double travelTimeFromPreviousMinutes = 0.0;
                TransitPathType transitPathType = null;
                boolean routeEstimated = false;

                if (index > 0) {
                    distanceFromPreviousKm =
                            routeMatrix.getDistanceKm(index - 1, index);
                    travelTimeFromPreviousMinutes =
                            routeMatrix.getTravelTimeMinutes(
                                    index - 1,
                                    index
                            );
                    transitPathType = routeMatrix.getTransitPathType(
                            index - 1,
                            index
                    );
                    routeEstimated = routeMatrix.isEstimated(
                            index - 1,
                            index
                    );
                    estimatedTravelTimes |= routeEstimated;
                }

                totalDistanceKm += distanceFromPreviousKm;
                totalTravelTimeMinutes += travelTimeFromPreviousMinutes;
                optimizedPlaces.add(toOptimizedPlace(
                        dailyCandidates.get(index),
                        index + 1,
                        distanceFromPreviousKm,
                        travelTimeFromPreviousMinutes,
                        transitPathType,
                        routeEstimated
                ));
            }
        }

        int totalVisitTimeMinutes = optimizedPlaces.stream()
                .mapToInt(OptimizedPlaceDto::getExpectedVisitMinutes)
                .sum();
        return CourseOptimizeResponse.builder()
                .transportMode(transportMode)
                .estimatedTravelTimes(estimatedTravelTimes)
                .optimizedPlaces(optimizedPlaces)
                .totalDistanceKm(totalDistanceKm)
                .totalTravelTimeMinutes(totalTravelTimeMinutes)
                .totalVisitTimeMinutes(totalVisitTimeMinutes)
                .totalCourseTimeMinutes(
                        totalVisitTimeMinutes + totalTravelTimeMinutes
                )
                .build();
    }

    /** 교체가 반영된 후보 목록을 날짜별 방문 순서로 정렬하고 합계를 다시 계산한다. */
    private CourseOptimizeResponse optimizeCandidates(
            List<PlaceCandidateDto> candidates,
            TransportMode transportMode,
            Map<LocalDate, Long> preferredFirstPlaceIdsByDate,
            boolean resolveActualRouteLegs,
            boolean estimateCandidatePool
    ) {

        // 중복을 먼저 제거한 뒤 TreeMap으로 날짜가 빠른 일정부터 처리한다.
        Map<LocalDate, List<PlaceCandidateDto>> candidatesByDate = new TreeMap<>();
        for (PlaceCandidateDto candidate : candidates) {
            candidatesByDate
                    .computeIfAbsent(candidate.getVisitDate(), ignored -> new ArrayList<>())
                    .add(candidate);
        }

        List<OptimizedPlaceDto> optimizedPlaces = new ArrayList<>();
        double totalDistanceKm = 0.0;
        double totalTravelTimeMinutes = 0.0;
        boolean estimatedTravelTimes = false;

        // 하루마다 이동 행렬을 한 번 계산하고 최근접 이웃 + 2-opt로 경로를 만든다.
        for (Map.Entry<LocalDate, List<PlaceCandidateDto>> entry
                : candidatesByDate.entrySet()) {
            List<PlaceCandidateDto> dailyCandidates = entry.getValue();
            // 추천 생성과 대중교통은 후보 전체 쌍의 외부 호출을 피하기 위해
            // 추정 행렬로 방문 순서와 대체 여부를 먼저 결정한다.
            boolean useEstimatedCandidateMatrix =
                    estimateCandidatePool
                            || transportMode == TransportMode.PUBLIC_TRANSIT;
            RouteMatrix routeMatrix =
                    useEstimatedCandidateMatrix
                            ? distanceService.calculateCandidatePoolMatrix(
                                    dailyCandidates,
                                    transportMode
                            )
                            : distanceService.calculateRouteMatrix(
                                    dailyCandidates,
                                    transportMode
                            );
            List<Integer> routeIndexes = createNearestNeighborRoute(
                    dailyCandidates,
                    routeMatrix,
                    preferredFirstPlaceIdsByDate == null
                            ? null
                            : preferredFirstPlaceIdsByDate.get(entry.getKey())
            );
            routeIndexes = improveRouteWithTwoOpt(routeIndexes, routeMatrix);
            RouteMatrix resultRouteMatrix =
                    resolveActualRouteLegs
                            && useEstimatedCandidateMatrix
                    ? distanceService.calculateRouteLegMatrix(
                            dailyCandidates,
                            transportMode,
                            routeIndexes
                    )
                    : routeMatrix;

            // 2-opt가 끝난 최종 순서를 기준으로 장소별 이동값과 전체 합계를 계산한다.
            for (int routePosition = 0; routePosition < routeIndexes.size(); routePosition++) {
                int currentIndex = routeIndexes.get(routePosition);
                double distanceFromPreviousKm = 0.0;
                double travelTimeFromPreviousMinutes = 0.0;
                TransitPathType transitPathType = null;
                boolean routeEstimated = false;
                if (routePosition > 0) {
                    int previousIndex = routeIndexes.get(routePosition - 1);
                    distanceFromPreviousKm =
                            resultRouteMatrix.getDistanceKm(
                                    previousIndex,
                                    currentIndex
                            );
                    travelTimeFromPreviousMinutes =
                            resultRouteMatrix.getTravelTimeMinutes(
                                    previousIndex,
                                    currentIndex
                            );
                    // 이전→현재 방향의 ODsay 경로 종류를 현재 장소의 이동 정보로 귀속한다.
                    transitPathType = resultRouteMatrix.getTransitPathType(
                            previousIndex,
                            currentIndex
                    );
                    routeEstimated = resultRouteMatrix.isEstimated(
                            previousIndex,
                            currentIndex
                    );
                    estimatedTravelTimes |= routeEstimated;
                }

                totalDistanceKm += distanceFromPreviousKm;
                totalTravelTimeMinutes += travelTimeFromPreviousMinutes;
                optimizedPlaces.add(toOptimizedPlace(
                        dailyCandidates.get(currentIndex),
                        routePosition + 1,
                        distanceFromPreviousKm,
                        travelTimeFromPreviousMinutes,
                        transitPathType,
                        routeEstimated
                ));
            }
        }

        // 전체 소요시간은 장소 체류시간과 장소 사이 이동시간을 합산한다.
        int totalVisitTimeMinutes = optimizedPlaces.stream()
                .mapToInt(OptimizedPlaceDto::getExpectedVisitMinutes)
                .sum();
        double totalCourseTimeMinutes =
                totalVisitTimeMinutes + totalTravelTimeMinutes;

        return CourseOptimizeResponse.builder()
                .transportMode(transportMode)
                .estimatedTravelTimes(estimatedTravelTimes)
                .optimizedPlaces(optimizedPlaces)
                .totalDistanceKm(totalDistanceKm)
                .totalTravelTimeMinutes(totalTravelTimeMinutes)
                .totalVisitTimeMinutes(totalVisitTimeMinutes)
                .totalCourseTimeMinutes(totalCourseTimeMinutes)
                .build();
    }

    /** 추천 점수가 가장 높은 첫 장소에서 시작하는 최근접 이웃 초기 경로를 만든다. */
    private List<Integer> createNearestNeighborRoute(
            List<PlaceCandidateDto> candidates,
            RouteMatrix routeMatrix,
            Long preferredFirstPlaceId
    ) {
        List<Integer> remainingIndexes = createIndexes(candidates.size());
        List<Integer> routeIndexes = new ArrayList<>(candidates.size());
        int currentIndex = selectFirstPlaceIndex(
                candidates,
                preferredFirstPlaceId
        );
        routeIndexes.add(currentIndex);
        remainingIndexes.remove(Integer.valueOf(currentIndex));

        while (!remainingIndexes.isEmpty()) {
            int nextIndex = selectNextPlaceIndex(
                    currentIndex,
                    remainingIndexes,
                    candidates,
                    routeMatrix
            );
            routeIndexes.add(nextIndex);
            remainingIndexes.remove(Integer.valueOf(nextIndex));
            currentIndex = nextIndex;
        }
        return routeIndexes;
    }

    /**
     * 첫 장소는 유지하고 이후 연속 구간을 뒤집어 더 짧은 열린 경로를 찾는다.
     * 총 이동시간을 우선하며, 시간이 같을 때만 총 거리를 비교한다.
     */
    private List<Integer> improveRouteWithTwoOpt(
            List<Integer> initialRoute,
            RouteMatrix routeMatrix
    ) {
        if (initialRoute.size() < 3) {
            return initialRoute;
        }

        List<Integer> bestRoute = new ArrayList<>(initialRoute);
        RouteCost bestCost = calculateRouteCost(bestRoute, routeMatrix);

        while (true) {
            List<Integer> improvedRoute = null;
            RouteCost improvedCost = bestCost;

            // 0번 출발 장소는 추천 점수 정책을 지키기 위해 뒤집기 대상에서 제외한다.
            for (int start = 1; start < bestRoute.size() - 1; start++) {
                for (int end = start + 1; end < bestRoute.size(); end++) {
                    List<Integer> candidateRoute = reverseSegment(
                            bestRoute,
                            start,
                            end
                    );
                    RouteCost candidateCost = calculateRouteCost(
                            candidateRoute,
                            routeMatrix
                    );

                    if (isBetterRouteCost(candidateCost, improvedCost)) {
                        improvedRoute = candidateRoute;
                        improvedCost = candidateCost;
                    }
                }
            }

            if (improvedRoute == null) {
                return bestRoute;
            }
            bestRoute = improvedRoute;
            bestCost = improvedCost;
        }
    }

    /** 원본 순서를 변경하지 않고 지정한 양 끝을 포함한 구간만 뒤집는다. */
    private List<Integer> reverseSegment(
            List<Integer> route,
            int start,
            int end
    ) {
        List<Integer> reversed = new ArrayList<>(route);
        Collections.reverse(reversed.subList(start, end + 1));
        return reversed;
    }

    /** 비대칭 경로 행렬도 정확히 비교할 수 있도록 경로 전체 비용을 다시 합산한다. */
    private RouteCost calculateRouteCost(
            List<Integer> route,
            RouteMatrix routeMatrix
    ) {
        double totalTravelTimeMinutes = 0.0;
        double totalDistanceKm = 0.0;

        for (int index = 1; index < route.size(); index++) {
            int previousIndex = route.get(index - 1);
            int currentIndex = route.get(index);
            totalTravelTimeMinutes += routeMatrix.getTravelTimeMinutes(
                    previousIndex,
                    currentIndex
            );
            totalDistanceKm += routeMatrix.getDistanceKm(
                    previousIndex,
                    currentIndex
            );
        }
        return new RouteCost(totalTravelTimeMinutes, totalDistanceKm);
    }

    /** 이동시간을 우선 비교하고 동률일 때만 거리의 엄격한 개선을 허용한다. */
    private boolean isBetterRouteCost(RouteCost candidate, RouteCost current) {
        boolean faster = candidate.travelTimeMinutes()
                < current.travelTimeMinutes() - ROUTE_TIE_EPSILON;
        boolean sameTravelTime = Math.abs(
                candidate.travelTimeMinutes() - current.travelTimeMinutes()
        ) <= ROUTE_TIE_EPSILON;
        boolean shorterAtSameTime = sameTravelTime
                && candidate.distanceKm()
                < current.distanceKm() - ROUTE_TIE_EPSILON;

        return faster || shorterAtSameTime;
    }

    /** 현재 최적화 결과의 먼 구간을 앞에서부터 확인해 실제 사용할 첫 교체안을 찾는다. */
    private Replacement findReplacement(
            CourseOptimizeResponse optimized,
            List<PlaceCandidateDto> currentCandidates,
            Map<Long, List<PlaceCandidateDto>> alternativesByCurrentPlaceId,
            List<PlaceCandidateDto> legacyAlternatives,
            Set<Long> originalPlaceIds,
            Set<Long> currentCoursePlaceIds,
            Set<Long> consumedAlternativeIds,
            TransportMode transportMode,
            boolean estimateCandidatePool
    ) {
        List<OptimizedPlaceDto> optimizedPlaces = optimized.getOptimizedPlaces();

        for (int index = 1; index < optimizedPlaces.size(); index++) {
            OptimizedPlaceDto distantPlace = optimizedPlaces.get(index);

            // 방문 순서가 1이면 새 날짜의 첫 장소이므로 전날 마지막 장소와 연결하지 않는다.
            if (distantPlace.getVisitOrder() == 1 || !isDistantLeg(
                    distantPlace.getDistanceFromPreviousKm(),
                    distantPlace.getTravelTimeFromPreviousMinutes(),
                    transportMode
            )) {
                continue;
            }

            PlaceCandidateDto previousCandidate = findCandidateByPlaceId(
                    currentCandidates,
                    optimizedPlaces.get(index - 1).getPlaceId()
            );
            PlaceCandidateDto distantCandidate = findCandidateByPlaceId(
                    currentCandidates,
                    distantPlace.getPlaceId()
            );
            PlaceCandidateDto nextCandidate = null;
            if (index + 1 < optimizedPlaces.size()) {
                OptimizedPlaceDto nextPlace = optimizedPlaces.get(index + 1);
                boolean sameDay = distantPlace.getVisitDate().equals(
                        nextPlace.getVisitDate()
                );
                if (sameDay && nextPlace.getVisitOrder() != 1) {
                    nextCandidate = findCandidateByPlaceId(
                            currentCandidates,
                            nextPlace.getPlaceId()
                    );
                }
            }
            List<PlaceCandidateDto> candidateAlternatives =
                    alternativesByCurrentPlaceId.get(distantCandidate.getPlaceId());
            if (candidateAlternatives == null) {
                candidateAlternatives = legacyAlternatives;
            }

            PlaceCandidateDto alternative = selectBestAlternative(
                    previousCandidate,
                    distantCandidate,
                    nextCandidate,
                    candidateAlternatives,
                    originalPlaceIds,
                    currentCoursePlaceIds,
                    consumedAlternativeIds,
                    transportMode,
                    estimateCandidatePool
            );

            if (alternative != null) {
                return new Replacement(distantCandidate, alternative);
            }
        }

        return null;
    }

    /**
     * 이동시간을 주 기준으로 먼 구간을 판단하고 거리는 보조 기준으로만 사용한다.
     * 거리만 긴데 실제 이동시간은 충분히 짧은 자동차 구간이 강제 교체되지 않도록 한다.
     */
    private boolean isDistantLeg(
            double distanceKm,
            double travelTimeMinutes,
            TransportMode transportMode
    ) {
        double maxTravelMinutes = maxTravelMinutes(transportMode);
        if (travelTimeMinutes > maxTravelMinutes) {
            return true;
        }
        return distanceKm > maxDistanceKm(transportMode)
                && travelTimeMinutes > maxTravelMinutes * 0.75;
    }

    /** 팀에서 조정하기 쉽도록 이동수단별 초기 거리 기준을 한곳에 모은다. */
    private double maxDistanceKm(TransportMode transportMode) {
        return switch (transportMode) {
            case WALKING -> 2.0;
            case PUBLIC_TRANSIT -> 10.0;
            case DRIVING -> 15.0;
        };
    }

    /** 팀에서 조정하기 쉽도록 이동수단별 초기 이동시간 기준을 한곳에 모은다. */
    private double maxTravelMinutes(TransportMode transportMode) {
        return switch (transportMode) {
            case WALKING -> 30.0;
            case PUBLIC_TRANSIT -> 45.0;
            case DRIVING -> 40.0;
        };
    }

    /**
     * 날짜·카테고리·중복 조건을 만족하며 교체 전후 구간이 허용 기준 안인 후보 중
     * 앞 구간과 다음 구간의 합산 이동시간 → 합산 거리 → 추천 점수 → 장소 ID
     * 순으로 가장 좋은 후보를 고른다. 마지막 장소는 기존처럼 앞 구간만 비교한다.
     */
    private PlaceCandidateDto selectBestAlternative(
            PlaceCandidateDto previousPlace,
            PlaceCandidateDto distantPlace,
            PlaceCandidateDto nextPlace,
            List<PlaceCandidateDto> alternatives,
            Set<Long> originalPlaceIds,
            Set<Long> currentCoursePlaceIds,
            Set<Long> consumedAlternativeIds,
            TransportMode transportMode,
            boolean estimateCandidatePool
    ) {
        List<PlaceCandidateDto> eligibleAlternatives = alternatives.stream()
                .filter(alternative -> alternative.getVisitDate().equals(
                        distantPlace.getVisitDate()
                ))
                .filter(alternative -> isSameCategory(
                        alternative.getCategory(),
                        distantPlace.getCategory()
                ))
                .filter(alternative -> !originalPlaceIds.contains(alternative.getPlaceId()))
                .filter(alternative -> !currentCoursePlaceIds.contains(alternative.getPlaceId()))
                .filter(alternative -> !consumedAlternativeIds.contains(alternative.getPlaceId()))
                .toList();

        if (eligibleAlternatives.isEmpty()) {
            return null;
        }

        List<PlaceCandidateDto> matrixCandidates = new ArrayList<>();
        matrixCandidates.add(previousPlace);
        matrixCandidates.addAll(eligibleAlternatives);
        int nextPlaceMatrixIndex = -1;
        if (nextPlace != null) {
            nextPlaceMatrixIndex = matrixCandidates.size();
            matrixCandidates.add(nextPlace);
        }
        boolean useEstimatedCandidateMatrix =
                estimateCandidatePool
                        || transportMode == TransportMode.PUBLIC_TRANSIT;
        RouteMatrix routeMatrix =
                useEstimatedCandidateMatrix
                        ? distanceService.calculateCandidatePoolMatrix(
                                matrixCandidates,
                                transportMode
                        )
                        : distanceService.calculateRouteMatrix(
                                matrixCandidates,
                                transportMode
                        );

        int selectedMatrixIndex = -1;
        double shortestTravelTimeMinutes = Double.MAX_VALUE;
        double shortestDistanceKm = Double.MAX_VALUE;
        int alternativeEndIndex = nextPlaceMatrixIndex < 0
                ? matrixCandidates.size()
                : nextPlaceMatrixIndex;

        // 0번은 이전 장소이고 1번부터 alternativeEndIndex 직전까지 실제 대체 후보이다.
        for (int matrixIndex = 1; matrixIndex < alternativeEndIndex; matrixIndex++) {
            double previousDistanceKm = routeMatrix.getDistanceKm(0, matrixIndex);
            double previousTravelTimeMinutes = routeMatrix.getTravelTimeMinutes(
                    0,
                    matrixIndex
            );
            if (isDistantLeg(
                    previousDistanceKm,
                    previousTravelTimeMinutes,
                    transportMode
            )) {
                continue;
            }

            double nextDistanceKm = 0.0;
            double nextTravelTimeMinutes = 0.0;
            if (nextPlaceMatrixIndex >= 0) {
                nextDistanceKm = routeMatrix.getDistanceKm(
                        matrixIndex,
                        nextPlaceMatrixIndex
                );
                nextTravelTimeMinutes = routeMatrix.getTravelTimeMinutes(
                        matrixIndex,
                        nextPlaceMatrixIndex
                );
                if (isDistantLeg(
                        nextDistanceKm,
                        nextTravelTimeMinutes,
                        transportMode
                )) {
                    continue;
                }
            }

            double totalTravelTimeMinutes = previousTravelTimeMinutes
                    + nextTravelTimeMinutes;
            double totalDistanceKm = previousDistanceKm + nextDistanceKm;
            PlaceCandidateDto candidate = matrixCandidates.get(matrixIndex);
            boolean faster = totalTravelTimeMinutes
                    < shortestTravelTimeMinutes - ROUTE_TIE_EPSILON;
            boolean sameTravelTime = Math.abs(
                    totalTravelTimeMinutes - shortestTravelTimeMinutes
            ) <= ROUTE_TIE_EPSILON;
            boolean shorterAtSameTime = sameTravelTime
                    && totalDistanceKm < shortestDistanceKm - ROUTE_TIE_EPSILON;
            boolean sameRouteCost = sameTravelTime
                    && Math.abs(
                    totalDistanceKm - shortestDistanceKm
            ) <= ROUTE_TIE_EPSILON;

            if (faster
                    || shorterAtSameTime
                    || (sameRouteCost && (selectedMatrixIndex < 0 || isPreferredCandidate(
                    candidate,
                    matrixCandidates.get(selectedMatrixIndex)
            )))) {
                selectedMatrixIndex = matrixIndex;
                shortestTravelTimeMinutes = totalTravelTimeMinutes;
                shortestDistanceKm = totalDistanceKm;
            }
        }

        return selectedMatrixIndex < 0
                ? null
                : matrixCandidates.get(selectedMatrixIndex);
    }

    /** 영문·한글 별칭이 달라도 같은 PLACES 기본 카테고리라면 교체를 허용한다. */
    private boolean isSameCategory(String firstCategory, String secondCategory) {
        return normalizeCategory(firstCategory).equals(normalizeCategory(secondCategory));
    }

    private String normalizeCategory(String category) {
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "tour", "관광지", "attraction", "tourist_attraction" -> "tour";
            case "restaurant", "식당", "음식점" -> "restaurant";
            case "cafe", "café", "카페" -> "cafe";
            case "hotel", "숙소", "호텔", "accommodation", "lodging" -> "hotel";
            default -> normalized;
        };
    }

    /** 장소 ID로 현재 코스 후보를 찾는다. ID 중복은 앞 단계에서 이미 제거된다. */
    private PlaceCandidateDto findCandidateByPlaceId(
            List<PlaceCandidateDto> candidates,
            Long placeId
    ) {
        return candidates.stream()
                .filter(candidate -> candidate.getPlaceId().equals(placeId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "최적화 결과와 장소 후보 목록이 일치하지 않습니다."
                ));
    }

    /** 기존 후보의 위치에 대체 장소를 넣고, 이후 전체 경로를 다시 계산한다. */
    private void replaceCandidateByPlaceId(
            List<PlaceCandidateDto> candidates,
            Long placeId,
            PlaceCandidateDto replacement
    ) {
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).getPlaceId().equals(placeId)) {
                candidates.set(index, replacement);
                return;
            }
        }
    }

    /**
     * 각 원본 장소에 포함된 전용 대체 후보를 검증하고 장소 ID별 후보 풀로 만든다.
     * 대체 후보 JSON에는 방문 날짜가 없어도 되며 부모 장소의 날짜를 자동 상속한다.
     */
    private Map<Long, List<PlaceCandidateDto>> createNestedAlternativePools(
            List<PlaceCandidateDto> candidates
    ) {
        Map<Long, List<PlaceCandidateDto>> pools = new LinkedHashMap<>();

        for (PlaceCandidateDto candidate : candidates) {
            List<PlaceCandidateDto> datedAlternatives = new ArrayList<>();
            for (PlaceCandidateDto alternative : alternativesOf(candidate)) {
                datedAlternatives.add(inheritVisitDate(
                        alternative,
                        candidate.getVisitDate()
                ));
            }

            List<PlaceCandidateDto> alternatives = validateAndRemoveDuplicates(
                    datedAlternatives
            );
            if (!alternatives.isEmpty()) {
                pools.put(candidate.getPlaceId(), alternatives);
            }
        }

        return pools;
    }

    /** JSON에서 대체 후보 배열이 null로 들어와도 빈 목록으로 처리한다. */
    private List<PlaceCandidateDto> alternativesOf(PlaceCandidateDto candidate) {
        return candidate.getAlternativeCandidates() == null
                ? Collections.emptyList()
                : candidate.getAlternativeCandidates();
    }

    /** 대체 후보가 원본 장소의 방문 날짜를 상속하도록 새 객체로 복사한다. */
    private PlaceCandidateDto inheritVisitDate(
            PlaceCandidateDto alternative,
            LocalDate visitDate
    ) {
        if (alternative == null) {
            throw new IllegalArgumentException("장소 후보는 null일 수 없습니다.");
        }
        if (alternative.getVisitDate() != null
                && !visitDate.equals(alternative.getVisitDate())) {
            throw new IllegalArgumentException(
                    "대체 후보의 방문 날짜는 원본 장소와 같아야 합니다. placeId="
                            + alternative.getPlaceId()
            );
        }

        return PlaceCandidateDto.builder()
                .placeId(alternative.getPlaceId())
                .placeName(alternative.getPlaceName())
                .category(alternative.getCategory())
                .region(alternative.getRegion())
                .address(alternative.getAddress())
                .roadAddress(alternative.getRoadAddress())
                .imageUrl(alternative.getImageUrl())
                .recommendationScore(alternative.getRecommendationScore())
                .latitude(alternative.getLatitude())
                .longitude(alternative.getLongitude())
                .visitDate(visitDate)
                .themePalaceCultureYn(alternative.getThemePalaceCultureYn())
                .themeNatureHangangYn(alternative.getThemeNatureHangangYn())
                .themeDateYn(alternative.getThemeDateYn())
                .themeFoodTourYn(alternative.getThemeFoodTourYn())
                .themeCafeTourYn(alternative.getThemeCafeTourYn())
                .themeShoppingHotplaceYn(alternative.getThemeShoppingHotplaceYn())
                .themeNightViewYn(alternative.getThemeNightViewYn())
                .themeHotelStayYn(alternative.getThemeHotelStayYn())
                .alternativeCandidates(alternativesOf(alternative))
                .build();
    }

    /**
     * 같은 장소가 여러 번 들어오면 코스 전체에서 한 번만 사용한다.
     * 날짜가 다르면 더 이른 날짜의 후보를 남기고, 같은 날짜라면 추천 점수가
     * 더 높은 후보를 남긴다. 날짜와 점수까지 같으면 먼저 받은 후보를 유지한다.
     */
    private List<PlaceCandidateDto> validateAndRemoveDuplicates(
            List<PlaceCandidateDto> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, PlaceCandidateDto> uniqueCandidates = new LinkedHashMap<>();

        for (PlaceCandidateDto candidate : candidates) {
            validateCandidate(candidate);
            uniqueCandidates.merge(
                    candidate.getPlaceId(),
                    candidate,
                    this::selectDuplicateCandidate
            );
        }

        return new ArrayList<>(uniqueCandidates.values());
    }

    /** 중복 장소 중 더 이른 날짜, 같은 날짜라면 추천 점수가 높은 후보를 선택한다. */
    private PlaceCandidateDto selectDuplicateCandidate(
            PlaceCandidateDto existing,
            PlaceCandidateDto candidate
    ) {
        int dateComparison = candidate.getVisitDate().compareTo(existing.getVisitDate());
        if (dateComparison < 0) {
            return candidate;
        }
        if (dateComparison > 0) {
            return existing;
        }

        return candidate.getRecommendationScore() > existing.getRecommendationScore()
                ? candidate
                : existing;
    }

    /** 경로 행렬과 같은 위치 체계를 사용하도록 0부터 시작하는 후보 인덱스를 만든다. */
    private List<Integer> createIndexes(int size) {
        List<Integer> indexes = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            indexes.add(index);
        }
        return indexes;
    }

    /** 하루의 출발 장소를 추천 점수 우선, 장소 ID 보조 기준으로 결정한다. */
    private int selectFirstPlaceIndex(
            List<PlaceCandidateDto> candidates,
            Long preferredFirstPlaceId
    ) {
        if (preferredFirstPlaceId != null) {
            for (int index = 0; index < candidates.size(); index++) {
                if (preferredFirstPlaceId.equals(candidates.get(index).getPlaceId())) {
                    return index;
                }
            }
        }

        int firstIndex = 0;

        for (int index = 1; index < candidates.size(); index++) {
            if (isPreferredCandidate(candidates.get(index), candidates.get(firstIndex))) {
                firstIndex = index;
            }
        }

        return firstIndex;
    }

    /** 이동시간 → 거리 → 추천 점수 → 장소 ID 순으로 다음 방문 장소를 결정한다. */
    private int selectNextPlaceIndex(
            int currentIndex,
            List<Integer> candidateIndexes,
            List<PlaceCandidateDto> candidates,
            RouteMatrix routeMatrix
    ) {
        int selectedIndex = -1;
        double shortestTravelTimeMinutes = Double.MAX_VALUE;
        double shortestDistanceKm = Double.MAX_VALUE;

        for (int candidateIndex : candidateIndexes) {
            double travelTimeMinutes =
                    routeMatrix.getTravelTimeMinutes(currentIndex, candidateIndex);
            double distanceKm = routeMatrix.getDistanceKm(currentIndex, candidateIndex);

            boolean faster =
                    travelTimeMinutes < shortestTravelTimeMinutes - ROUTE_TIE_EPSILON;
            boolean sameTravelTime =
                    Math.abs(travelTimeMinutes - shortestTravelTimeMinutes) <= ROUTE_TIE_EPSILON;
            boolean shorterAtSameTime = sameTravelTime
                    && distanceKm < shortestDistanceKm - ROUTE_TIE_EPSILON;
            boolean sameRouteCost = sameTravelTime
                    && Math.abs(distanceKm - shortestDistanceKm) <= ROUTE_TIE_EPSILON;

            if (faster
                    || shorterAtSameTime
                    || (sameRouteCost && (selectedIndex < 0 || isPreferredCandidate(
                    candidates.get(candidateIndex),
                    candidates.get(selectedIndex)
            )))) {
                selectedIndex = candidateIndex;
                shortestTravelTimeMinutes = travelTimeMinutes;
                shortestDistanceKm = distanceKm;
            }
        }

        return selectedIndex;
    }

    /** 추천 점수가 같으면 항상 같은 결과가 나오도록 더 작은 장소 ID를 우선한다. */
    private boolean isPreferredCandidate(
            PlaceCandidateDto candidate,
            PlaceCandidateDto currentCandidate
    ) {
        int scoreComparison = Double.compare(
                candidate.getRecommendationScore(),
                currentCandidate.getRecommendationScore()
        );

        return scoreComparison > 0
                || (scoreComparison == 0
                && candidate.getPlaceId() < currentCandidate.getPlaceId());
    }

    /** 원본 후보에 계산된 체류시간·방문 순서·이동 정보를 더해 응답 DTO로 변환한다. */
    private OptimizedPlaceDto toOptimizedPlace(
            PlaceCandidateDto candidate,
            int visitOrder,
            double distanceFromPreviousKm,
            double travelTimeFromPreviousMinutes,
            TransitPathType transitPathType,
            boolean routeEstimated
    ) {
        return OptimizedPlaceDto.builder()
                .placeId(candidate.getPlaceId())
                .placeName(candidate.getPlaceName())
                .category(candidate.getCategory())
                .region(candidate.getRegion())
                .address(candidate.getAddress())
                .roadAddress(candidate.getRoadAddress())
                .imageUrl(candidate.getImageUrl())
                .recommendationScore(candidate.getRecommendationScore())
                .latitude(candidate.getLatitude())
                .longitude(candidate.getLongitude())
                .visitDate(candidate.getVisitDate())
                .themePalaceCultureYn(candidate.getThemePalaceCultureYn())
                .themeNatureHangangYn(candidate.getThemeNatureHangangYn())
                .themeDateYn(candidate.getThemeDateYn())
                .themeFoodTourYn(candidate.getThemeFoodTourYn())
                .themeCafeTourYn(candidate.getThemeCafeTourYn())
                .themeShoppingHotplaceYn(candidate.getThemeShoppingHotplaceYn())
                .themeNightViewYn(candidate.getThemeNightViewYn())
                .themeHotelStayYn(candidate.getThemeHotelStayYn())
                .expectedVisitMinutes(
                        visitDurationService.calculateExpectedVisitMinutes(
                                candidate.getCategory()
                        )
                )
                .visitOrder(visitOrder)
                .distanceFromPreviousKm(distanceFromPreviousKm)
                .travelTimeFromPreviousMinutes(travelTimeFromPreviousMinutes)
                .transitPathType(transitPathType)
                .routeEstimated(routeEstimated)
                .build();
    }

    /** 최적화와 거리 계산에 필요한 장소 후보의 필수값을 한곳에서 검증한다. */
    private void validateCandidate(PlaceCandidateDto candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("장소 후보는 null일 수 없습니다.");
        }
        if (candidate.getPlaceId() == null) {
            throw new IllegalArgumentException("장소 ID는 필수입니다.");
        }
        if (candidate.getPlaceName() == null || candidate.getPlaceName().isBlank()) {
            throw new IllegalArgumentException("장소명은 필수입니다.");
        }
        if (candidate.getCategory() == null || candidate.getCategory().isBlank()) {
            throw new IllegalArgumentException("장소 카테고리는 필수입니다.");
        }
        if (candidate.getRecommendationScore() == null
                || !Double.isFinite(candidate.getRecommendationScore())) {
            throw new IllegalArgumentException("추천 점수는 유한한 숫자여야 합니다.");
        }
        if (candidate.getLatitude() == null || candidate.getLongitude() == null) {
            throw new IllegalArgumentException("장소의 위도와 경도는 필수입니다.");
        }
        if (candidate.getVisitDate() == null) {
            throw new IllegalArgumentException("방문 날짜는 필수입니다.");
        }

        // 동일 좌표 간 거리 계산을 호출해 실제 계산 전에 위도·경도 범위까지 검증한다.
        distanceService.calculateDistanceKm(
                candidate.getLatitude(),
                candidate.getLongitude(),
                candidate.getLatitude(),
                candidate.getLongitude()
        );
    }

    /** 2-opt 후보 경로의 전체 이동시간과 거리를 함께 비교하는 값이다. */
    private record RouteCost(
            double travelTimeMinutes,
            double distanceKm
    ) {
    }

    /** 먼 구간에서 제거할 장소와 그 자리에 넣을 대체 후보이다. */
    private record Replacement(
            PlaceCandidateDto distantPlace,
            PlaceCandidateDto alternativePlace
    ) {
    }
}
