package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.domain.course.routing.DrivingRouteCalculator;
import com.seoulink.backend.domain.course.routing.PublicTransitRouteCalculator;
import com.seoulink.backend.domain.course.routing.RouteCalculation;
import com.seoulink.backend.domain.course.routing.RouteCalculator;
import com.seoulink.backend.domain.course.routing.RouteCalculatorFactory;
import com.seoulink.backend.domain.course.routing.WalkingRouteCalculator;
import com.seoulink.backend.domain.course.service.RoutePairCache.RoutePairValue;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 장소 사이의 거리와 이동시간을 계산하는 서비스이다.
 *
 * <p>동일 이동수단·장소 쌍은 메모리 캐시에서 우선 조회한다. 캐시에 없는 쌍은
 * {@link RouteCalculatorFactory}가 도보·대중교통·자동차 계산기로 분기하며,
 * 외부 API를 사용할 수 없는 경우 계산기가 명시적인 추정값을 반환한다.</p>
 */
@Service
public class DistanceService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final RouteCalculatorFactory routeCalculatorFactory;
    private final RoutePairCache routePairCache;

    /** Spring 실행 시 등록된 이동수단 계산기 팩토리와 장소 쌍 캐시를 주입한다. */
    @Autowired
    public DistanceService(
            RouteCalculatorFactory routeCalculatorFactory,
            RoutePairCache routePairCache
    ) {
        this.routeCalculatorFactory = routeCalculatorFactory;
        this.routePairCache = routePairCache;
    }

    /** 기존 단위 테스트와 수동 생성 코드에서 사용하는 생성자이다. */
    public DistanceService(OpenRouteServiceClient openRouteServiceClient) {
        this(openRouteServiceClient, null, new RoutePairCache());
    }

    /** 외부 클라이언트와 캐시를 직접 지정하는 단위 테스트용 생성자이다. */
    DistanceService(
            OpenRouteServiceClient openRouteServiceClient,
            RoutePairCache routePairCache
    ) {
        this(openRouteServiceClient, null, routePairCache);
    }

    /** ORS·ODsay 클라이언트와 캐시를 직접 지정하는 대중교통 단위 테스트용 생성자이다. */
    DistanceService(
            OpenRouteServiceClient openRouteServiceClient,
            OdsayClient odsayClient,
            RoutePairCache routePairCache
    ) {
        this(
                createRouteCalculatorFactory(openRouteServiceClient, odsayClient),
                routePairCache
        );
    }

    /** 외부 API 없이 실행하는 순수 단위 테스트에서 사용한다. */
    DistanceService() {
        this((OpenRouteServiceClient) null);
    }

    private static RouteCalculatorFactory createRouteCalculatorFactory(
            OpenRouteServiceClient openRouteServiceClient,
            OdsayClient odsayClient
    ) {
        return new RouteCalculatorFactory(List.of(
                new WalkingRouteCalculator(openRouteServiceClient),
                new DrivingRouteCalculator(openRouteServiceClient),
                new PublicTransitRouteCalculator(odsayClient)
        ));
    }

    /**
     * 하루 장소 전체의 경로 거리·시간 행렬을 계산한다.
     *
     * <p>캐시에 이미 있는 장소 쌍은 외부 API를 다시 호출하지 않는다. 일부 쌍만
     * 누락된 경우 외부 행렬을 한 번 요청해 누락값만 채우며, 전체가 캐시 적중이면
     * 외부 요청을 생략한다.</p>
     */
    public RouteMatrix calculateRouteMatrix(
            List<PlaceCandidateDto> candidates,
            TransportMode transportMode
    ) {
        if (candidates == null) {
            throw new IllegalArgumentException("장소 후보 목록은 null일 수 없습니다.");
        }
        if (transportMode == null) {
            throw new IllegalArgumentException("이동수단은 필수입니다.");
        }

        validateCoordinates(candidates);
        int size = candidates.size();
        double[][] distancesKm = new double[size][size];
        double[][] travelTimesMinutes = new double[size][size];
        boolean[][] estimatedPairs = new boolean[size][size];
        TransitPathType[][] transitPathTypes = new TransitPathType[size][size];
        boolean[][] missingPairs = new boolean[size][size];
        CacheLoadResult cacheLoadResult = loadCachedPairs(
                candidates,
                transportMode,
                distancesKm,
                travelTimesMinutes,
                estimatedPairs,
                transitPathTypes,
                missingPairs
        );

        if (!cacheLoadResult.hasMissingPair()) {
            return new RouteMatrix(
                    distancesKm,
                    travelTimesMinutes,
                    estimatedPairs,
                    transitPathTypes
            );
        }

        RouteCalculator calculator = routeCalculatorFactory.get(transportMode);
        List<RouteCoordinate> coordinates = candidates.stream()
                .map(candidate -> new RouteCoordinate(
                        candidate.getLongitude(),
                        candidate.getLatitude()
                ))
                .toList();
        RouteCalculation calculation = calculator.calculate(
                coordinates,
                missingPairs
        );
        fillMissingPairsFromCalculation(
                candidates,
                transportMode,
                calculation,
                distancesKm,
                travelTimesMinutes,
                estimatedPairs,
                transitPathTypes,
                missingPairs
        );
        return new RouteMatrix(
                distancesKm,
                travelTimesMinutes,
                estimatedPairs,
                transitPathTypes
        );
    }

    /**
     * 추천 조합을 만들기 위한 대규모 후보 풀의 1차 거리·시간 행렬을 계산한다.
     *
     * <p>ODsay는 장소 쌍 단일 호출 API이므로 대중교통 후보 풀 전체를 실제 API로
     * 계산하면 후보 수의 제곱에 비례해 호출량이 커진다. 따라서 이 단계만 외부 호출
     * 없는 대중교통 추정값을 사용하고, 선택된 최종 코스는 {@link #calculateRouteMatrix}
     * 에서 ODsay 실제 값으로 다시 최적화한다. 도보·자동차는 기존 ORS 행렬을 사용한다.</p>
     */
    public RouteMatrix calculateCandidatePoolMatrix(
            List<PlaceCandidateDto> candidates,
            TransportMode transportMode
    ) {
        if (transportMode != TransportMode.PUBLIC_TRANSIT) {
            return calculateRouteMatrix(candidates, transportMode);
        }
        if (candidates == null) {
            throw new IllegalArgumentException("장소 후보 목록은 null일 수 없습니다.");
        }

        validateCoordinates(candidates);
        List<RouteCoordinate> coordinates = candidates.stream()
                .map(candidate -> new RouteCoordinate(
                        candidate.getLongitude(),
                        candidate.getLatitude()
                ))
                .toList();
        RouteCalculation estimation = routeCalculatorFactory
                .get(transportMode)
                .estimate(coordinates);
        return new RouteMatrix(
                estimation.distancesKm(),
                estimation.travelTimesMinutes(),
                estimation.estimatedPairs(),
                estimation.transitPathTypes()
        );
    }

    /** 이전 도보 전용 호출부를 위한 임시 호환 메서드이다. 신규 코드는 이동수단을 전달한다. */
    @Deprecated
    public RouteMatrix calculateRouteMatrix(List<PlaceCandidateDto> candidates) {
        return calculateRouteMatrix(candidates, TransportMode.WALKING);
    }

    /** 캐시 적중값을 행렬에 채우고 하나라도 누락되었는지 반환한다. */
    private CacheLoadResult loadCachedPairs(
            List<PlaceCandidateDto> candidates,
            TransportMode transportMode,
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean[][] estimatedPairs,
            TransitPathType[][] transitPathTypes,
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
                        candidates.get(toIndex),
                        transportMode
                );
                if (cached.isPresent()) {
                    RoutePairValue value = cached.get();
                    distancesKm[fromIndex][toIndex] = value.distanceKm();
                    travelTimesMinutes[fromIndex][toIndex] = value.travelTimeMinutes();
                    estimatedPairs[fromIndex][toIndex] = value.estimated();
                    transitPathTypes[fromIndex][toIndex] = value.transitPathType();
                } else {
                    missingPairs[fromIndex][toIndex] = true;
                    hasMissingPair = true;
                }
            }
        }
        return new CacheLoadResult(hasMissingPair);
    }

    /** 이동수단 계산 결과에서 캐시에 없던 장소 쌍만 채우고 캐시에 저장한다. */
    private void fillMissingPairsFromCalculation(
            List<PlaceCandidateDto> candidates,
            TransportMode transportMode,
            RouteCalculation calculation,
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean[][] estimatedPairs,
            TransitPathType[][] transitPathTypes,
            boolean[][] missingPairs
    ) {
        if (calculation.distancesKm().length != candidates.size()) {
            throw new IllegalStateException("이동수단 계산 결과의 행렬 크기가 올바르지 않습니다.");
        }
        for (int fromIndex = 0; fromIndex < candidates.size(); fromIndex++) {
            for (int toIndex = 0; toIndex < candidates.size(); toIndex++) {
                if (!missingPairs[fromIndex][toIndex]) {
                    continue;
                }

                double distanceKm = calculation.getDistanceKm(fromIndex, toIndex);
                double travelTimeMinutes =
                        calculation.getTravelTimeMinutes(fromIndex, toIndex);
                storePair(
                        candidates,
                        transportMode,
                        fromIndex,
                        toIndex,
                        distanceKm,
                        travelTimeMinutes,
                        calculation.isEstimated(fromIndex, toIndex),
                        calculation.getTransitPathType(fromIndex, toIndex),
                        distancesKm,
                        travelTimesMinutes,
                        estimatedPairs,
                        transitPathTypes
                );
            }
        }
    }

    /** 계산한 한 방향 장소 쌍 값을 반환 행렬과 장기 재사용 캐시에 함께 기록한다. */
    private void storePair(
            List<PlaceCandidateDto> candidates,
            TransportMode transportMode,
            int fromIndex,
            int toIndex,
            double distanceKm,
            double travelTimeMinutes,
            boolean estimated,
            TransitPathType transitPathType,
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean[][] estimatedPairs,
            TransitPathType[][] transitPathTypes
    ) {
        distancesKm[fromIndex][toIndex] = distanceKm;
        travelTimesMinutes[fromIndex][toIndex] = travelTimeMinutes;
        estimatedPairs[fromIndex][toIndex] = estimated;
        transitPathTypes[fromIndex][toIndex] = transitPathType;
        routePairCache.put(
                candidates.get(fromIndex),
                candidates.get(toIndex),
                transportMode,
                distanceKm,
                travelTimeMinutes,
                estimated,
                transitPathType
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

    /** 장소 인덱스를 기준으로 거리·이동시간·대중교통 경로 종류를 조회하는 행렬이다. */
    public record RouteMatrix(
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean[][] estimatedPairs,
            TransitPathType[][] transitPathTypes
    ) {
        public RouteMatrix {
            validateMatrix(
                    distancesKm,
                    travelTimesMinutes,
                    estimatedPairs,
                    transitPathTypes
            );
        }

        /** 대중교통 경로 종류가 없던 기존 호출부용 호환 생성자이다. */
        public RouteMatrix(
                double[][] distancesKm,
                double[][] travelTimesMinutes,
                boolean[][] estimatedPairs
        ) {
            this(
                    distancesKm,
                    travelTimesMinutes,
                    estimatedPairs,
                    createEmptyTransitPathTypes(distancesKm)
            );
        }

        /** 전체 행렬이 실제값 또는 추정값인 기존 호출부용 호환 생성자이다. */
        public RouteMatrix(
                double[][] distancesKm,
                double[][] travelTimesMinutes,
                boolean estimatedTravelTimes
        ) {
            this(
                    distancesKm,
                    travelTimesMinutes,
                    createEstimatedPairs(distancesKm, estimatedTravelTimes),
                    createEmptyTransitPathTypes(distancesKm)
            );
        }

        public RouteMatrix(
                double[][] distancesKm,
                double[][] travelTimesMinutes
        ) {
            this(distancesKm, travelTimesMinutes, false);
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

        public boolean isEstimated(int fromIndex, int toIndex) {
            return estimatedPairs[fromIndex][toIndex];
        }

        public TransitPathType getTransitPathType(int fromIndex, int toIndex) {
            return transitPathTypes[fromIndex][toIndex];
        }

        public boolean estimatedTravelTimes() {
            for (boolean[] row : estimatedPairs) {
                for (boolean estimated : row) {
                    if (estimated) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static void validateMatrix(
                double[][] distancesKm,
                double[][] travelTimesMinutes,
                boolean[][] estimatedPairs,
                TransitPathType[][] transitPathTypes
        ) {
            if (distancesKm == null
                    || travelTimesMinutes == null
                    || estimatedPairs == null
                    || transitPathTypes == null
                    || distancesKm.length != travelTimesMinutes.length
                    || distancesKm.length != estimatedPairs.length
                    || distancesKm.length != transitPathTypes.length) {
                throw new IllegalArgumentException("거리·시간 행렬 크기가 올바르지 않습니다.");
            }

            int size = distancesKm.length;
            for (int row = 0; row < size; row++) {
                if (distancesKm[row] == null
                        || travelTimesMinutes[row] == null
                        || estimatedPairs[row] == null
                        || transitPathTypes[row] == null
                        || distancesKm[row].length != size
                        || travelTimesMinutes[row].length != size
                        || estimatedPairs[row].length != size
                        || transitPathTypes[row].length != size) {
                    throw new IllegalArgumentException("거리·시간 행렬은 정사각형이어야 합니다.");
                }
            }
        }

        private static boolean[][] createEstimatedPairs(
                double[][] distancesKm,
                boolean estimated
        ) {
            if (distancesKm == null) {
                return null;
            }
            int size = distancesKm.length;
            boolean[][] result = new boolean[size][size];
            if (!estimated) {
                return result;
            }

            for (int fromIndex = 0; fromIndex < size; fromIndex++) {
                for (int toIndex = 0; toIndex < size; toIndex++) {
                    result[fromIndex][toIndex] = fromIndex != toIndex;
                }
            }
            return result;
        }

        private static TransitPathType[][] createEmptyTransitPathTypes(
                double[][] distancesKm
        ) {
            return distancesKm == null
                    ? null
                    : new TransitPathType[distancesKm.length][distancesKm.length];
        }
    }

    private record CacheLoadResult(boolean hasMissingPair) {
    }
}
