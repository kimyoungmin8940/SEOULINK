package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient.OdsayApiException;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient.TransitRouteResult;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteMatrixResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** ODsay 대중교통 결과를 코스 최적화용 거리·시간 행렬로 변환한다. */
@Component
public class PublicTransitRouteCalculator implements RouteCalculator {

    private static final Logger log = LoggerFactory.getLogger(
            PublicTransitRouteCalculator.class
    );
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double ROUTE_DISTANCE_FACTOR = 1.20;
    private static final double ACCESS_WALKING_DISTANCE_KM = 0.70;
    private static final double DIRECT_WALKING_SKIP_DISTANCE_KM = 0.20;
    private static final double WALKING_SPEED_KM_PER_HOUR = 4.5;
    private static final double TRANSIT_SPEED_KM_PER_HOUR = 22.0;
    private static final double WAIT_AND_TRANSFER_MINUTES = 8.0;

    private final OdsayClient odsayClient;
    private final OpenRouteServiceClient openRouteServiceClient;

    public PublicTransitRouteCalculator(
            OdsayClient odsayClient,
            OpenRouteServiceClient openRouteServiceClient
    ) {
        this.odsayClient = odsayClient;
        this.openRouteServiceClient = openRouteServiceClient;
    }

    @Override
    public TransportMode supportedMode() {
        return TransportMode.PUBLIC_TRANSIT;
    }

    @Override
    public RouteCalculation calculate(List<RouteCoordinate> coordinates) {
        if (coordinates == null) {
            throw new IllegalArgumentException("대중교통 계산 좌표 목록은 null일 수 없습니다.");
        }
        int size = coordinates.size();
        boolean[][] requiredPairs = new boolean[size][size];
        for (int fromIndex = 0; fromIndex < size; fromIndex++) {
            for (int toIndex = 0; toIndex < size; toIndex++) {
                requiredPairs[fromIndex][toIndex] = fromIndex != toIndex;
            }
        }
        return calculate(coordinates, requiredPairs);
    }

    @Override
    public RouteCalculation calculate(
            List<RouteCoordinate> coordinates,
            boolean[][] requiredPairs
    ) {
        validateRequiredPairs(coordinates, requiredPairs);
        RouteCalculation fallback = estimate(coordinates);

        if (!hasRequiredPair(requiredPairs)) {
            return new RouteCalculation(
                    fallback.distancesKm(),
                    fallback.travelTimesMinutes(),
                    false
            );
        }
        double[][] distancesKm = fallback.distancesKm();
        double[][] travelTimesMinutes = fallback.travelTimesMinutes();
        boolean[][] estimatedPairs = fallback.estimatedPairs();
        TransitPathType[][] transitPathTypes = fallback.transitPathTypes();
        boolean apiAvailable = odsayClient != null
                && odsayClient.isConfigured();
        if (!apiAvailable) {
            log.warn(
                    "ODsay API 키가 설정되지 않아 대중교통 구간을 추정값으로 표시합니다."
            );
        }

        for (int fromIndex = 0; fromIndex < coordinates.size(); fromIndex++) {
            for (int toIndex = 0; toIndex < coordinates.size(); toIndex++) {
                if (!requiredPairs[fromIndex][toIndex]) {
                    continue;
                }

                RouteCoordinate from = coordinates.get(fromIndex);
                RouteCoordinate to = coordinates.get(toIndex);
                double straightDistanceKm = straightLineDistanceKm(from, to);
                if (straightDistanceKm <= DIRECT_WALKING_SKIP_DISTANCE_KM) {
                    applyWalkingRoute(
                            from,
                            to,
                            fromIndex,
                            toIndex,
                            distancesKm,
                            travelTimesMinutes,
                            estimatedPairs,
                            transitPathTypes
                    );
                    log.info(
                            "200m 이내 초단거리 구간은 ODsay 호출 없이 도보 경로로 전환합니다: straightDistanceKm={}, from=({}, {}), to=({}, {})",
                            roundForLog(straightDistanceKm),
                            from.longitude(),
                            from.latitude(),
                            to.longitude(),
                            to.latitude()
                    );
                    continue;
                }
                if (!apiAvailable) {
                    log.warn(
                            "ODsay 전역 사용 불가 상태라 구간을 추정값으로 유지합니다: straightDistanceKm={}, from=({}, {}), to=({}, {})",
                            roundForLog(straightDistanceKm),
                            from.longitude(),
                            from.latitude(),
                            to.longitude(),
                            to.latitude()
                    );
                    continue;
                }

                try {
                    log.info(
                            "ODsay 구간 조회 시작: straightDistanceKm={}, from=({}, {}), to=({}, {})",
                            roundForLog(straightDistanceKm),
                            from.longitude(),
                            from.latitude(),
                            to.longitude(),
                            to.latitude()
                    );
                    TransitRouteResult result = odsayClient.calculateRoute(
                            from,
                            to
                    );
                    distancesKm[fromIndex][toIndex] = result.distanceKm();
                    travelTimesMinutes[fromIndex][toIndex] =
                            result.travelTimeMinutes();
                    transitPathTypes[fromIndex][toIndex] =
                            result.transitPathType();
                    estimatedPairs[fromIndex][toIndex] = false;
                } catch (OdsayApiException exception) {
                    if (exception.isPairSpecific()) {
                        applyWalkingRoute(
                                coordinates.get(fromIndex),
                                coordinates.get(toIndex),
                                fromIndex,
                                toIndex,
                                distancesKm,
                                travelTimesMinutes,
                                estimatedPairs,
                                transitPathTypes
                        );
                        log.info(
                                "ODsay 구간 검색 불가로 도보 경로를 사용합니다: code={}, straightDistanceKm={}, from=({}, {}), to=({}, {})",
                                exception.getErrorCode(),
                                roundForLog(straightDistanceKm),
                                from.longitude(),
                                from.latitude(),
                                to.longitude(),
                                to.latitude()
                        );
                        continue;
                    }

                    if (isHardGlobalFailure(exception)) {
                        apiAvailable = false;
                        log.warn(
                                "ODsay 호출 한도·차단 오류로 현재 요청의 남은 구간은 임시 추정값을 사용합니다: code={}, straightDistanceKm={}, reason={}",
                                exception.getErrorCode(),
                                roundForLog(straightDistanceKm),
                                exception.getMessage()
                        );
                    } else {
                        log.warn(
                                "ODsay 구간 오류가 발생했지만 다음 구간은 다시 실제 조회합니다: code={}, straightDistanceKm={}, reason={}",
                                exception.getErrorCode(),
                                roundForLog(straightDistanceKm),
                                exception.getMessage()
                        );
                    }
                } catch (RuntimeException exception) {
                    log.warn(
                            "ODsay 구간 호출 실패로 해당 구간만 추정값을 유지하고 다음 구간은 계속 실제 조회합니다: straightDistanceKm={}, reason={}",
                            roundForLog(straightDistanceKm),
                            exception.getMessage()
                    );
                    // 네트워크 오류나 특정 응답 파싱 실패가 연속되어도 남은 장소쌍을
                    // 임의로 건너뛰지 않는다. 인증·쿼터·HTTP 차단처럼 명확한 전역
                    // 오류만 위의 isHardGlobalFailure 분기에서 현재 요청을 중단한다.
                }
            }
        }

        return new RouteCalculation(
                distancesKm,
                travelTimesMinutes,
                estimatedPairs,
                transitPathTypes
        );
    }


    /** 실제 일일 한도·HTTP 429처럼 재시도해도 즉시 성공할 수 없는 오류만 전역 중단한다. */
    private boolean isHardGlobalFailure(OdsayApiException exception) {
        if (exception == null) {
            return false;
        }
        String code = exception.getErrorCode();
        String message = exception.getApiMessage() == null
                ? ""
                : exception.getApiMessage().toLowerCase();
        return "LOCAL_DAILY_LIMIT".equals(code)
                || "HTTP_401".equals(code)
                || "HTTP_403".equals(code)
                || "HTTP_429".equals(code)
                || message.contains("apikey")
                || message.contains("api key")
                || message.contains("authentication")
                || message.contains("unauthorized")
                || message.contains("forbidden")
                || message.contains("quota")
                || message.contains("limit")
                || message.contains("exceed")
                || message.contains("suspend")
                || message.contains("blocked")
                || message.contains("인증")
                || message.contains("호출 한도")
                || message.contains("사용량")
                || message.contains("초과")
                || message.contains("정지");
    }

    private void applyWalkingRoute(
            RouteCoordinate from,
            RouteCoordinate to,
            int fromIndex,
            int toIndex,
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean[][] estimatedPairs,
            TransitPathType[][] transitPathTypes
    ) {
        transitPathTypes[fromIndex][toIndex] = TransitPathType.WALKING;

        if (openRouteServiceClient != null
                && openRouteServiceClient.isConfigured()) {
            try {
                RouteMatrixResult walking = openRouteServiceClient.calculateMatrix(
                        "foot-walking",
                        List.of(from, to)
                );
                double distanceKm = walking.getDistanceKm(0, 1);
                double travelMinutes = walking.getTravelTimeMinutes(0, 1);
                if (Double.isFinite(distanceKm)
                        && Double.isFinite(travelMinutes)
                        && distanceKm >= 0.0
                        && travelMinutes >= 0.0) {
                    distancesKm[fromIndex][toIndex] = distanceKm;
                    travelTimesMinutes[fromIndex][toIndex] = travelMinutes;
                    estimatedPairs[fromIndex][toIndex] = false;
                    return;
                }
            } catch (RuntimeException exception) {
                log.debug(
                        "ODsay -98 구간의 ORS 도보 경로 조회 실패: {}",
                        exception.getMessage()
                );
            }
        }

        RouteCalculation walkingFallback = RouteEstimationSupport.estimate(
                List.of(from, to),
                1.15,
                distanceKm -> distanceKm / WALKING_SPEED_KM_PER_HOUR * 60.0
        );
        distancesKm[fromIndex][toIndex] = walkingFallback.getDistanceKm(0, 1);
        travelTimesMinutes[fromIndex][toIndex] =
                walkingFallback.getTravelTimeMinutes(0, 1);
        estimatedPairs[fromIndex][toIndex] = true;
    }

    /** 로그와 초단거리 판정에 공통으로 사용하는 두 좌표의 직선거리(km)이다. */
    private double straightLineDistanceKm(
            RouteCoordinate from,
            RouteCoordinate to
    ) {
        double latitudeDeltaRadians = Math.toRadians(
                to.latitude() - from.latitude()
        );
        double longitudeDeltaRadians = Math.toRadians(
                to.longitude() - from.longitude()
        );
        double fromLatitudeRadians = Math.toRadians(from.latitude());
        double toLatitudeRadians = Math.toRadians(to.latitude());
        double haversine = Math.sin(latitudeDeltaRadians / 2.0)
                * Math.sin(latitudeDeltaRadians / 2.0)
                + Math.cos(fromLatitudeRadians)
                * Math.cos(toLatitudeRadians)
                * Math.sin(longitudeDeltaRadians / 2.0)
                * Math.sin(longitudeDeltaRadians / 2.0);
        double clampedHaversine = Math.max(0.0, Math.min(1.0, haversine));
        double centralAngle = 2.0 * Math.atan2(
                Math.sqrt(clampedHaversine),
                Math.sqrt(1.0 - clampedHaversine)
        );
        return EARTH_RADIUS_KM * centralAngle;
    }

    private double roundForLog(double value) {
        return Math.round(value * 1_000.0) / 1_000.0;
    }

    @Override
    public RouteCalculation estimate(List<RouteCoordinate> coordinates) {
        if (coordinates == null) {
            throw new IllegalArgumentException("대중교통 계산 좌표 목록은 null일 수 없습니다.");
        }
        return RouteEstimationSupport.estimate(
                coordinates,
                ROUTE_DISTANCE_FACTOR,
                this::estimateTransitMinutes
        );
    }

    private void validateRequiredPairs(
            List<RouteCoordinate> coordinates,
            boolean[][] requiredPairs
    ) {
        if (coordinates == null || requiredPairs == null
                || requiredPairs.length != coordinates.size()) {
            throw new IllegalArgumentException("ODsay 계산 대상 행렬 크기가 올바르지 않습니다.");
        }
        for (boolean[] row : requiredPairs) {
            if (row == null || row.length != coordinates.size()) {
                throw new IllegalArgumentException(
                        "ODsay 계산 대상 행렬은 정사각형이어야 합니다."
                );
            }
        }
    }

    private boolean hasRequiredPair(boolean[][] requiredPairs) {
        for (boolean[] row : requiredPairs) {
            for (boolean required : row) {
                if (required) {
                    return true;
                }
            }
        }
        return false;
    }

    double estimateTransitMinutes(double routeDistanceKm) {
        if (routeDistanceKm == 0.0) {
            return 0.0;
        }
        if (routeDistanceKm <= ACCESS_WALKING_DISTANCE_KM) {
            return routeDistanceKm / WALKING_SPEED_KM_PER_HOUR * 60.0;
        }

        double walkingDistanceKm = Math.min(
                routeDistanceKm,
                ACCESS_WALKING_DISTANCE_KM
        );
        double transitDistanceKm = Math.max(
                0.0,
                routeDistanceKm - walkingDistanceKm
        );
        return walkingDistanceKm / WALKING_SPEED_KM_PER_HOUR * 60.0
                + transitDistanceKm / TRANSIT_SPEED_KM_PER_HOUR * 60.0
                + WAIT_AND_TRANSFER_MINUTES;
    }
}
