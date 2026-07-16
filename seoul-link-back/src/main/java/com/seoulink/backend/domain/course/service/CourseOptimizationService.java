package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import com.seoulink.backend.domain.course.service.DistanceService.RouteMatrix;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 추천 장소 후보를 날짜별로 나누고 실제 이동시간이 짧은 순서로 정렬하는 서비스이다.
 *
 * <p>중복 장소를 한 번만 남긴 뒤 각 날짜에서 추천 점수가 가장 높은 장소를 첫 장소로 선택하고,
 * OpenRouteService 경로 행렬의 이동시간을 기준으로 다음 장소를 선택한다.
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

        // 중복을 먼저 제거한 뒤 TreeMap으로 날짜가 빠른 일정부터 처리한다.
        Map<LocalDate, List<PlaceCandidateDto>> candidatesByDate = new TreeMap<>();
        for (PlaceCandidateDto candidate : validateAndRemoveDuplicates(candidates)) {
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

    /**
     * 같은 장소가 여러 번 들어오면 코스 전체에서 한 번만 사용한다.
     * 날짜가 다르면 더 이른 날짜의 후보를 남기고, 같은 날짜라면 추천 점수가
     * 더 높은 후보를 남긴다. 날짜와 점수까지 같으면 먼저 받은 후보를 유지한다.
     */
    private List<PlaceCandidateDto> validateAndRemoveDuplicates(
            List<PlaceCandidateDto> candidates
    ) {
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
}
