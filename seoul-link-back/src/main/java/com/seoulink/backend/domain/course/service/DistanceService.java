package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.service.RoutePairCache.RoutePairValue;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteMatrixResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 장소 사이의 거리와 이동시간을 계산하는 서비스이다.
 *
 * <p>동일 장소 쌍은 메모리 캐시에서 우선 조회한다. 캐시에 없는 쌍이 있으면
 * OpenRouteService의 실제 도보 경로 행렬을 사용하고, 키가 없거나 요청이 실패하면
 * Haversine 직선거리와 평균 도보 속도로 계산한 값을 캐시에 저장해 재사용한다.</p>
 */
@Service
public class DistanceService {

    private static final Logger log = LoggerFactory.getLogger(DistanceService.class);
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double FALLBACK_WALKING_SPEED_KM_PER_HOUR = 4.5;

    private final OpenRouteServiceClient openRouteServiceClient;
    private final RoutePairCache routePairCache;

    /** Spring 실행 시 외부 경로 클라이언트와 장소 쌍 캐시를 주입한다. */
    @Autowired
    public DistanceService(
            OpenRouteServiceClient openRouteServiceClient,
            RoutePairCache routePairCache
    ) {
        this.openRouteServiceClient = openRouteServiceClient;
        this.routePairCache = routePairCache;
    }

    /** 기존 단위 테스트와 수동 생성 코드에서 사용하는 생성자이다. */
    public DistanceService(OpenRouteServiceClient openRouteServiceClient) {
        this(openRouteServiceClient, new RoutePairCache());
    }

    /** 외부 API 없이 실행하는 순수 단위 테스트에서 사용한다. */
    DistanceService() {
        this(null, new RoutePairCache());
    }

    /**
     * 하루 장소 전체의 경로 거리·시간 행렬을 계산한다.
     *
     * <p>캐시에 이미 있는 장소 쌍은 외부 API를 다시 호출하지 않는다. 일부 쌍만
     * 누락된 경우 외부 행렬을 한 번 요청해 누락값만 채우며, 전체가 캐시 적중이면
     * 외부 요청을 생략한다.</p>
     */
    public RouteMatrix calculateRouteMatrix(List<PlaceCandidateDto> candidates) {
        if (candidates == null) {
            throw new IllegalArgumentException("장소 후보 목록은 null일 수 없습니다.");
        }

        validateCoordinates(candidates);
        int size = candidates.size();
        double[][] distancesKm = new double[size][size];
        double[][] travelTimesMinutes = new double[size][size];
        boolean[][] missingPairs = new boolean[size][size];
        boolean hasMissingPair = loadCachedPairs(
                candidates,
                distancesKm,
                travelTimesMinutes,
                missingPairs
        );

        if (!hasMissingPair) {
            return new RouteMatrix(distancesKm, travelTimesMinutes);
        }

        if (size >= 2
                && openRouteServiceClient != null
                && openRouteServiceClient.isConfigured()) {
            try {
                List<RouteCoordinate> coordinates = candidates.stream()
                        .map(candidate -> new RouteCoordinate(
                                candidate.getLongitude(),
                                candidate.getLatitude()
                        ))
                        .toList();

                RouteMatrixResult apiResult =
                        openRouteServiceClient.calculateMatrix(coordinates);
                fillMissingPairsFromApi(
                        candidates,
                        apiResult,
                        distancesKm,
                        travelTimesMinutes,
                        missingPairs
                );
                return new RouteMatrix(distancesKm, travelTimesMinutes);
            } catch (RuntimeException exception) {
                log.warn(
                        "OpenRouteService 호출 실패로 직선거리 계산을 사용합니다: {}",
                        exception.getMessage()
                );
            }
        }

        fillMissingPairsWithFallback(
                candidates,
                distancesKm,
                travelTimesMinutes,
                missingPairs
        );
        return new RouteMatrix(distancesKm, travelTimesMinutes);
    }

    /** 캐시 적중값을 행렬에 채우고 하나라도 누락되었는지 반환한다. */
    private boolean loadCachedPairs(
            List<PlaceCandidateDto> candidates,
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean[][] missingPairs
    ) {
        boolean hasMissingPair = false;
        for (int fromIndex = 0; fromIndex < candidates.size(); fromIndex++) {
            for (int toIndex = 0; toIndex < candidates.size(); toIndex++) {
                if (fromIndex == toIndex) {
                    distancesKm[fromIndex][toIndex] = 0.0;
                    travelTimesMinutes[fromIndex][toIndex] = 0.0;
                    continue;
                }

                Optional<RoutePairValue> cached = routePairCache.get(
                        candidates.get(fromIndex),
                        candidates.get(toIndex)
                );
                if (cached.isPresent()) {
                    RoutePairValue value = cached.get();
                    distancesKm[fromIndex][toIndex] = value.distanceKm();
                    travelTimesMinutes[fromIndex][toIndex] = value.travelTimeMinutes();
                } else {
                    missingPairs[fromIndex][toIndex] = true;
                    hasMissingPair = true;
                }
            }
        }
        return hasMissingPair;
    }

    /** 외부 API 행렬에서 캐시에 없던 장소 쌍만 채우고 캐시에 저장한다. */
    private void fillMissingPairsFromApi(
            List<PlaceCandidateDto> candidates,
            RouteMatrixResult apiResult,
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean[][] missingPairs
    ) {
        for (int fromIndex = 0; fromIndex < candidates.size(); fromIndex++) {
            for (int toIndex = 0; toIndex < candidates.size(); toIndex++) {
                if (!missingPairs[fromIndex][toIndex]) {
                    continue;
                }

                double distanceKm = apiResult.getDistanceKm(fromIndex, toIndex);
                double travelTimeMinutes =
                        apiResult.getTravelTimeMinutes(fromIndex, toIndex);
                storePair(
                        candidates,
                        fromIndex,
                        toIndex,
                        distanceKm,
                        travelTimeMinutes,
                        distancesKm,
                        travelTimesMinutes
                );
            }
        }
    }

    /** 누락된 장소 쌍을 Haversine과 평균 도보 속도로 계산해 캐시에 저장한다. */
    private void fillMissingPairsWithFallback(
            List<PlaceCandidateDto> candidates,
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean[][] missingPairs
    ) {
        for (int fromIndex = 0; fromIndex < candidates.size(); fromIndex++) {
            PlaceCandidateDto from = candidates.get(fromIndex);
            for (int toIndex = 0; toIndex < candidates.size(); toIndex++) {
                if (!missingPairs[fromIndex][toIndex]) {
                    continue;
                }

                PlaceCandidateDto to = candidates.get(toIndex);
                double distanceKm = calculateDistanceKm(
                        from.getLatitude(),
                        from.getLongitude(),
                        to.getLatitude(),
                        to.getLongitude()
                );
                double travelTimeMinutes =
                        distanceKm / FALLBACK_WALKING_SPEED_KM_PER_HOUR * 60.0;
                storePair(
                        candidates,
                        fromIndex,
                        toIndex,
                        distanceKm,
                        travelTimeMinutes,
                        distancesKm,
                        travelTimesMinutes
                );
            }
        }
    }

    /** 계산한 한 방향 장소 쌍 값을 반환 행렬과 장기 재사용 캐시에 함께 기록한다. */
    private void storePair(
            List<PlaceCandidateDto> candidates,
            int fromIndex,
            int toIndex,
            double distanceKm,
            double travelTimeMinutes,
            double[][] distancesKm,
            double[][] travelTimesMinutes
    ) {
        distancesKm[fromIndex][toIndex] = distanceKm;
        travelTimesMinutes[fromIndex][toIndex] = travelTimeMinutes;
        routePairCache.put(
                candidates.get(fromIndex),
                candidates.get(toIndex),
                distanceKm,
                travelTimeMinutes
        );
    }

    /**
     * 두 위도·경도 좌표 사이의 직선거리를 Haversine 공식으로 계산한다.
     *
     * @return 두 좌표 사이의 직선거리(km)
     */
    public double calculateDistanceKm(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        validateLatitude(startLatitude);
        validateLongitude(startLongitude);
        validateLatitude(endLatitude);
        validateLongitude(endLongitude);

        double startLatitudeRadians = Math.toRadians(startLatitude);
        double endLatitudeRadians = Math.toRadians(endLatitude);
        double latitudeDifference = Math.toRadians(endLatitude - startLatitude);
        double longitudeDifference = Math.toRadians(endLongitude - startLongitude);

        double haversine = Math.pow(Math.sin(latitudeDifference / 2), 2)
                + Math.cos(startLatitudeRadians)
                * Math.cos(endLatitudeRadians)
                * Math.pow(Math.sin(longitudeDifference / 2), 2);

        double normalizedHaversine = Math.max(0.0, Math.min(1.0, haversine));
        double centralAngle = 2 * Math.atan2(
                Math.sqrt(normalizedHaversine),
                Math.sqrt(1 - normalizedHaversine)
        );

        return EARTH_RADIUS_KM * centralAngle;
    }

    /** 행렬 계산 전에 모든 장소에 유효 범위의 위도·경도가 있는지 확인한다. */
    private void validateCoordinates(List<PlaceCandidateDto> candidates) {
        for (PlaceCandidateDto candidate : candidates) {
            if (candidate == null
                    || candidate.getLatitude() == null
                    || candidate.getLongitude() == null) {
                throw new IllegalArgumentException("장소의 위도와 경도는 필수입니다.");
            }
            validateLatitude(candidate.getLatitude());
            validateLongitude(candidate.getLongitude());
        }
    }

    /** 위도 범위(-90~90)와 유한값 여부를 검증한다. */
    private void validateLatitude(double latitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("위도는 -90 이상 90 이하의 유한한 숫자여야 합니다.");
        }
    }

    /** 경도 범위(-180~180)와 유한값 여부를 검증한다. */
    private void validateLongitude(double longitude) {
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("경도는 -180 이상 180 이하의 유한한 숫자여야 합니다.");
        }
    }

    /** 장소 인덱스를 기준으로 거리(km)와 이동시간(분)을 조회하는 행렬이다. */
    public record RouteMatrix(
            double[][] distancesKm,
            double[][] travelTimesMinutes
    ) {
        public RouteMatrix {
            validateMatrix(distancesKm, travelTimesMinutes);
        }

        public int size() {
            return distancesKm.length;
        }

        public double getDistanceKm(int fromIndex, int toIndex) {
            return distancesKm[fromIndex][toIndex];
        }

        public double getTravelTimeMinutes(int fromIndex, int toIndex) {
            return travelTimesMinutes[fromIndex][toIndex];
        }

        private static void validateMatrix(
                double[][] distancesKm,
                double[][] travelTimesMinutes
        ) {
            if (distancesKm == null
                    || travelTimesMinutes == null
                    || distancesKm.length != travelTimesMinutes.length) {
                throw new IllegalArgumentException("거리·시간 행렬 크기가 올바르지 않습니다.");
            }

            int size = distancesKm.length;
            for (int row = 0; row < size; row++) {
                if (distancesKm[row] == null
                        || travelTimesMinutes[row] == null
                        || distancesKm[row].length != size
                        || travelTimesMinutes[row].length != size) {
                    throw new IllegalArgumentException("거리·시간 행렬은 정사각형이어야 합니다.");
                }
            }
        }
    }
}
