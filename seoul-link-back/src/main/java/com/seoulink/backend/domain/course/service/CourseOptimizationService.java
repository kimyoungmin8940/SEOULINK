package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import com.seoulink.backend.domain.course.service.DistanceService.RouteMatrix;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
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
 * OpenRouteService 경로 행렬의 이동시간을 기준으로 다음 장소를 선택한다.
 * 한 구간의 이동거리 또는 이동시간이 허용 기준을 넘으면 같은 날짜·카테고리의
 * 예비 후보 중 기준 안에 들어오는 가장 가까운 장소로 교체한 뒤 전체 경로를 다시 계산한다.
 * 외부 API를 사용할 수 없으면 {@link DistanceService}가 직선거리 방식으로 자동 대체하고,
 * 예상 방문 시간은 {@link VisitDurationService}가 카테고리에 따라 계산한다.</p>
 */
@Service
public class CourseOptimizationService {

    // 외부 API의 부동소수점 오차 때문에 사실상 같은 경로 비용을 다르게 보지 않도록 한다.
    private static final double ROUTE_TIE_EPSILON = 0.000000001;

    // 두 장소 사이에서 하나라도 초과하면 먼 이동 구간으로 판단한다.
    private static final double MAX_LEG_DISTANCE_KM = 2.0;
    private static final double MAX_LEG_TRAVEL_TIME_MINUTES = 30.0;

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
        if (request == null) {
            throw new IllegalArgumentException("코스 최적화 요청은 null일 수 없습니다.");
        }

        List<PlaceCandidateDto> candidates = request.getPlaceCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return CourseOptimizeResponse.builder()
                    .optimizedPlaces(new ArrayList<>())
                    .totalDistanceKm(0.0)
                    .totalTravelTimeMinutes(0.0)
                    .totalVisitTimeMinutes(0)
                    .totalCourseTimeMinutes(0.0)
                    .build();
        }

        List<PlaceCandidateDto> currentCandidates = validateAndRemoveDuplicates(candidates);
        List<PlaceCandidateDto> alternatives = validateAndRemoveDuplicates(
                request.getAlternativeCandidates()
        );

        if (alternatives.isEmpty()) {
            return optimizeCandidates(currentCandidates);
        }

        // 원래 코스 장소와 이미 소비한 대체 후보가 다시 들어가지 않도록 ID를 추적한다.
        Set<Long> originalPlaceIds = new HashSet<>();
        Set<Long> currentCoursePlaceIds = new HashSet<>();
        for (PlaceCandidateDto candidate : currentCandidates) {
            originalPlaceIds.add(candidate.getPlaceId());
            currentCoursePlaceIds.add(candidate.getPlaceId());
        }
        Set<Long> consumedAlternativeIds = new HashSet<>();

        // 교체 후보 한 건은 최대 한 번만 사용하므로 후보 수만큼 반복하면 반드시 종료된다.
        for (int attempt = 0; attempt < alternatives.size(); attempt++) {
            CourseOptimizeResponse optimized = optimizeCandidates(currentCandidates);
            Replacement replacement = findReplacement(
                    optimized,
                    currentCandidates,
                    alternatives,
                    originalPlaceIds,
                    currentCoursePlaceIds,
                    consumedAlternativeIds
            );

            if (replacement == null) {
                return optimized;
            }

            replaceCandidateByPlaceId(
                    currentCandidates,
                    replacement.distantPlace().getPlaceId(),
                    replacement.alternativePlace()
            );
            currentCoursePlaceIds.remove(replacement.distantPlace().getPlaceId());
            currentCoursePlaceIds.add(replacement.alternativePlace().getPlaceId());
            consumedAlternativeIds.add(replacement.alternativePlace().getPlaceId());
        }

        return optimizeCandidates(currentCandidates);
    }

    /** 교체가 반영된 후보 목록을 날짜별 방문 순서로 정렬하고 합계를 다시 계산한다. */
    private CourseOptimizeResponse optimizeCandidates(List<PlaceCandidateDto> candidates) {

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

        // 하루마다 이동 행렬을 한 번 계산하고 최근접 이웃 방식으로 경로를 만든다.
        for (List<PlaceCandidateDto> dailyCandidates : candidatesByDate.values()) {
            RouteMatrix routeMatrix = distanceService.calculateRouteMatrix(dailyCandidates);
            List<Integer> remainingIndexes = createIndexes(dailyCandidates.size());
            int currentIndex = selectFirstPlaceIndex(dailyCandidates);
            remainingIndexes.remove(Integer.valueOf(currentIndex));

            // 첫 장소는 추천 점수가 가장 높은 후보이며 이전 장소 이동값은 0이다.
            int visitOrder = 1;
            optimizedPlaces.add(toOptimizedPlace(
                    dailyCandidates.get(currentIndex),
                    visitOrder,
                    0.0,
                    0.0
            ));

            // 현재 장소에서 이동시간이 가장 짧은 미방문 장소를 차례로 연결한다.
            while (!remainingIndexes.isEmpty()) {
                int nextIndex = selectNextPlaceIndex(
                        currentIndex,
                        remainingIndexes,
                        dailyCandidates,
                        routeMatrix
                );
                double distanceFromPreviousKm =
                        routeMatrix.getDistanceKm(currentIndex, nextIndex);
                double travelTimeFromPreviousMinutes =
                        routeMatrix.getTravelTimeMinutes(currentIndex, nextIndex);

                visitOrder++;
                totalDistanceKm += distanceFromPreviousKm;
                totalTravelTimeMinutes += travelTimeFromPreviousMinutes;
                optimizedPlaces.add(toOptimizedPlace(
                        dailyCandidates.get(nextIndex),
                        visitOrder,
                        distanceFromPreviousKm,
                        travelTimeFromPreviousMinutes
                ));

                remainingIndexes.remove(Integer.valueOf(nextIndex));
                currentIndex = nextIndex;
            }
        }

        // 전체 소요시간은 장소 체류시간과 장소 사이 이동시간을 합산한다.
        int totalVisitTimeMinutes = optimizedPlaces.stream()
                .mapToInt(OptimizedPlaceDto::getExpectedVisitMinutes)
                .sum();
        double totalCourseTimeMinutes =
                totalVisitTimeMinutes + totalTravelTimeMinutes;

        return CourseOptimizeResponse.builder()
                .optimizedPlaces(optimizedPlaces)
                .totalDistanceKm(totalDistanceKm)
                .totalTravelTimeMinutes(totalTravelTimeMinutes)
                .totalVisitTimeMinutes(totalVisitTimeMinutes)
                .totalCourseTimeMinutes(totalCourseTimeMinutes)
                .build();
    }

    /** 현재 최적화 결과의 먼 구간을 앞에서부터 확인해 실제 사용할 첫 교체안을 찾는다. */
    private Replacement findReplacement(
            CourseOptimizeResponse optimized,
            List<PlaceCandidateDto> currentCandidates,
            List<PlaceCandidateDto> alternatives,
            Set<Long> originalPlaceIds,
            Set<Long> currentCoursePlaceIds,
            Set<Long> consumedAlternativeIds
    ) {
        List<OptimizedPlaceDto> optimizedPlaces = optimized.getOptimizedPlaces();

        for (int index = 1; index < optimizedPlaces.size(); index++) {
            OptimizedPlaceDto distantPlace = optimizedPlaces.get(index);

            // 방문 순서가 1이면 새 날짜의 첫 장소이므로 전날 마지막 장소와 연결하지 않는다.
            if (distantPlace.getVisitOrder() == 1 || !isDistantLeg(
                    distantPlace.getDistanceFromPreviousKm(),
                    distantPlace.getTravelTimeFromPreviousMinutes()
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
            PlaceCandidateDto alternative = selectBestAlternative(
                    previousCandidate,
                    distantCandidate,
                    alternatives,
                    originalPlaceIds,
                    currentCoursePlaceIds,
                    consumedAlternativeIds
            );

            if (alternative != null) {
                return new Replacement(distantCandidate, alternative);
            }
        }

        return null;
    }

    /** 거리 2km 초과 또는 이동시간 30분 초과 구간인지 확인한다. */
    private boolean isDistantLeg(double distanceKm, double travelTimeMinutes) {
        return distanceKm > MAX_LEG_DISTANCE_KM
                || travelTimeMinutes > MAX_LEG_TRAVEL_TIME_MINUTES;
    }

    /**
     * 날짜·카테고리·중복 조건을 만족하며 새 구간도 허용 기준 안인 후보 중
     * 이동시간 → 거리 → 추천 점수 → 장소 ID 순으로 가장 좋은 후보를 고른다.
     */
    private PlaceCandidateDto selectBestAlternative(
            PlaceCandidateDto previousPlace,
            PlaceCandidateDto distantPlace,
            List<PlaceCandidateDto> alternatives,
            Set<Long> originalPlaceIds,
            Set<Long> currentCoursePlaceIds,
            Set<Long> consumedAlternativeIds
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
        RouteMatrix routeMatrix = distanceService.calculateRouteMatrix(matrixCandidates);

        int selectedMatrixIndex = -1;
        double shortestTravelTimeMinutes = Double.MAX_VALUE;
        double shortestDistanceKm = Double.MAX_VALUE;

        // 0번은 이전 장소이고 1번부터 실제 대체 후보이다.
        for (int matrixIndex = 1; matrixIndex < matrixCandidates.size(); matrixIndex++) {
            double distanceKm = routeMatrix.getDistanceKm(0, matrixIndex);
            double travelTimeMinutes = routeMatrix.getTravelTimeMinutes(0, matrixIndex);
            if (isDistantLeg(distanceKm, travelTimeMinutes)) {
                continue;
            }

            PlaceCandidateDto candidate = matrixCandidates.get(matrixIndex);
            boolean faster = travelTimeMinutes
                    < shortestTravelTimeMinutes - ROUTE_TIE_EPSILON;
            boolean sameTravelTime = Math.abs(
                    travelTimeMinutes - shortestTravelTimeMinutes
            ) <= ROUTE_TIE_EPSILON;
            boolean shorterAtSameTime = sameTravelTime
                    && distanceKm < shortestDistanceKm - ROUTE_TIE_EPSILON;
            boolean sameRouteCost = sameTravelTime
                    && Math.abs(distanceKm - shortestDistanceKm) <= ROUTE_TIE_EPSILON;

            if (faster
                    || shorterAtSameTime
                    || (sameRouteCost && (selectedMatrixIndex < 0 || isPreferredCandidate(
                    candidate,
                    matrixCandidates.get(selectedMatrixIndex)
            )))) {
                selectedMatrixIndex = matrixIndex;
                shortestTravelTimeMinutes = travelTimeMinutes;
                shortestDistanceKm = distanceKm;
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
    private int selectFirstPlaceIndex(List<PlaceCandidateDto> candidates) {
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
            double travelTimeFromPreviousMinutes
    ) {
        return OptimizedPlaceDto.builder()
                .placeId(candidate.getPlaceId())
                .placeName(candidate.getPlaceName())
                .category(candidate.getCategory())
                .recommendationScore(candidate.getRecommendationScore())
                .latitude(candidate.getLatitude())
                .longitude(candidate.getLongitude())
                .visitDate(candidate.getVisitDate())
                .expectedVisitMinutes(
                        visitDurationService.calculateExpectedVisitMinutes(
                                candidate.getCategory()
                        )
                )
                .visitOrder(visitOrder)
                .distanceFromPreviousKm(distanceFromPreviousKm)
                .travelTimeFromPreviousMinutes(travelTimeFromPreviousMinutes)
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

    /** 먼 구간에서 제거할 장소와 그 자리에 넣을 대체 후보이다. */
    private record Replacement(
            PlaceCandidateDto distantPlace,
            PlaceCandidateDto alternativePlace
    ) {
    }
}
