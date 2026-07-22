package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient.OdsayApiException;
import com.seoulink.backend.infrastructure.external.odsay.OdsayClient.TransitRouteResult;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ODsay 대중교통 길찾기 결과를 코스 최적화용 거리·시간 행렬로 변환한다.
 *
 * <p>ODsay는 행렬 API가 아니라 출발지·도착지 한 쌍씩 조회하므로 방향별로 호출한다.
 * API 키가 없거나 특정 구간 검색에 실패한 경우에만 별도 대중교통 추정값을 남기고
 * {@code estimated=true}로 표시한다. 호출 결과는 상위 {@code RoutePairCache}에서
 * 장소 쌍별로 재사용된다.</p>
 */
@Component
public class PublicTransitRouteCalculator implements RouteCalculator {

    private static final Logger log = LoggerFactory.getLogger(
            PublicTransitRouteCalculator.class
    );
    private static final double ROUTE_DISTANCE_FACTOR = 1.20;
    private static final double ACCESS_WALKING_DISTANCE_KM = 0.70;
    private static final double WALKING_SPEED_KM_PER_HOUR = 4.5;
    private static final double TRANSIT_SPEED_KM_PER_HOUR = 22.0;
    private static final double WAIT_AND_TRANSFER_MINUTES = 8.0;

    private final OdsayClient odsayClient;

    public PublicTransitRouteCalculator(OdsayClient odsayClient) {
        this.odsayClient = odsayClient;
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

    /** ODsay 단일 경로 API는 상위 캐시에 없는 방향별 장소 쌍만 호출한다. */
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
        if (odsayClient == null || !odsayClient.isConfigured()) {
            return fallback;
        }

        double[][] distancesKm = fallback.distancesKm();
        double[][] travelTimesMinutes = fallback.travelTimesMinutes();
        boolean[][] estimatedPairs = fallback.estimatedPairs();
        TransitPathType[][] transitPathTypes = fallback.transitPathTypes();
        boolean apiAvailable = true;

        for (int fromIndex = 0; fromIndex < coordinates.size(); fromIndex++) {
            for (int toIndex = 0; toIndex < coordinates.size(); toIndex++) {
                if (!requiredPairs[fromIndex][toIndex]) {
                    continue;
                }
                if (!apiAvailable) {
                    continue;
                }

                try {
                    TransitRouteResult result = odsayClient.calculateRoute(
                            coordinates.get(fromIndex),
                            coordinates.get(toIndex)
                    );
                    distancesKm[fromIndex][toIndex] = result.distanceKm();
                    travelTimesMinutes[fromIndex][toIndex] =
                            result.travelTimeMinutes();
                    transitPathTypes[fromIndex][toIndex] =
                            result.transitPathType();
                    estimatedPairs[fromIndex][toIndex] = false;
                } catch (OdsayApiException exception) {
                    if (OdsayClient.WITHIN_WALKING_DISTANCE_CODE.equals(
                            exception.getErrorCode()
                    )) {
                        travelTimesMinutes[fromIndex][toIndex] =
                                distancesKm[fromIndex][toIndex]
                                        / WALKING_SPEED_KM_PER_HOUR * 60.0;
                    }
                    // 검색 결과 없음처럼 구간 한정 오류는 다음 쌍을 계속 조회한다.
                    // 인증·요청 제한처럼 전체 호출에 영향을 주는 오류는 반복 요청을 중단한다.
                    if (exception.isPairSpecific()) {
                        log.debug(
                                "ODsay 구간 검색 결과가 없어 임시 추정값을 사용합니다. code={}",
                                exception.getErrorCode()
                        );
                    } else {
                        apiAvailable = false;
                        log.warn(
                                "ODsay 호출 오류로 남은 대중교통 구간에 임시 추정값을 사용합니다: {}",
                                exception.getMessage()
                        );
                    }
                } catch (RuntimeException exception) {
                    apiAvailable = false;
                    log.warn(
                            "ODsay 호출 실패로 남은 대중교통 구간에 임시 추정값을 사용합니다: {}",
                            exception.getMessage()
                    );
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

    /** 후보 풀 1차 평가에서는 ODsay를 호출하지 않고 대중교통 전용 추정값을 사용한다. */
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

    /** ODsay를 사용할 수 없는 구간의 개발용 추정 계산식을 별도로 격리한다. */
    double estimateTransitMinutes(double routeDistanceKm) {
        if (routeDistanceKm == 0.0) {
            return 0.0;
        }

        // ODsay가 -98(700m 이내)을 반환하는 짧은 구간은 도보 이동으로 처리한다.
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
