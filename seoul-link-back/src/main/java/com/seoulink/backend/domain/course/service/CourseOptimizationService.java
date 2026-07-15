package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.CourseOptimizeRequest;
import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.dto.response.CourseOptimizeResponse;
import com.seoulink.backend.domain.course.dto.response.OptimizedPlaceDto;
import com.seoulink.backend.domain.course.service.DistanceService.RouteMatrix;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 추천 장소 후보를 날짜별로 나누고 실제 이동시간이 짧은 순서로 정렬하는 서비스이다.
 *
 * <p>각 날짜에서 추천 점수가 가장 높은 장소를 첫 장소로 선택한 뒤,
 * OpenRouteService 경로 행렬의 이동시간을 기준으로 다음 장소를 선택한다.
 * 외부 API를 사용할 수 없으면 {@link DistanceService}가 직선거리 방식으로 자동 대체한다.</p>
 */
@Service
public class CourseOptimizationService {

    private static final double ROUTE_TIE_EPSILON = 0.000000001;

    private final DistanceService distanceService;

    public CourseOptimizationService(DistanceService distanceService) {
        this.distanceService = distanceService;
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
                    .build();
        }

        Map<LocalDate, List<PlaceCandidateDto>> candidatesByDate = new TreeMap<>();
        for (PlaceCandidateDto candidate : candidates) {
            validateCandidate(candidate);
            candidatesByDate
                    .computeIfAbsent(candidate.getVisitDate(), ignored -> new ArrayList<>())
                    .add(candidate);
        }

        List<OptimizedPlaceDto> optimizedPlaces = new ArrayList<>();
        double totalDistanceKm = 0.0;
        double totalTravelTimeMinutes = 0.0;

        for (List<PlaceCandidateDto> dailyCandidates : candidatesByDate.values()) {
            RouteMatrix routeMatrix = distanceService.calculateRouteMatrix(dailyCandidates);
            List<Integer> remainingIndexes = createIndexes(dailyCandidates.size());
            int currentIndex = selectFirstPlaceIndex(dailyCandidates);
            remainingIndexes.remove(Integer.valueOf(currentIndex));

            int visitOrder = 1;
            optimizedPlaces.add(toOptimizedPlace(
                    dailyCandidates.get(currentIndex),
                    visitOrder,
                    0.0,
                    0.0
            ));

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

        return CourseOptimizeResponse.builder()
                .optimizedPlaces(optimizedPlaces)
                .totalDistanceKm(totalDistanceKm)
                .totalTravelTimeMinutes(totalTravelTimeMinutes)
                .build();
    }

    private List<Integer> createIndexes(int size) {
        List<Integer> indexes = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            indexes.add(index);
        }
        return indexes;
    }

    private int selectFirstPlaceIndex(List<PlaceCandidateDto> candidates) {
        int firstIndex = 0;

        for (int index = 1; index < candidates.size(); index++) {
            if (isPreferredCandidate(candidates.get(index), candidates.get(firstIndex))) {
                firstIndex = index;
            }
        }

        return firstIndex;
    }

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
                .expectedVisitMinutes(candidate.getExpectedVisitMinutes())
                .visitOrder(visitOrder)
                .distanceFromPreviousKm(distanceFromPreviousKm)
                .travelTimeFromPreviousMinutes(travelTimeFromPreviousMinutes)
                .build();
    }

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
        if (candidate.getExpectedVisitMinutes() == null
                || candidate.getExpectedVisitMinutes() < 0) {
            throw new IllegalArgumentException("예상 방문 시간은 0분 이상이어야 합니다.");
        }

        distanceService.calculateDistanceKm(
                candidate.getLatitude(),
                candidate.getLongitude(),
                candidate.getLatitude(),
                candidate.getLongitude()
        );
    }
}
