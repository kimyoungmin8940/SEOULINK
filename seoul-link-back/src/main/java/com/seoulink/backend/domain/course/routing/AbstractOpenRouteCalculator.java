package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteMatrixResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleUnaryOperator;

/** 도보·자동차의 OpenRouteService 호출과 장애 시 추정 계산을 공통 처리한다. */
abstract class AbstractOpenRouteCalculator implements RouteCalculator {

    /** 첫 연결이나 ORS의 일시 오류로 첫 카드만 예상값이 되는 현상을 막기 위한 최대 시도 횟수이다. */
    private static final int MAX_ACTUAL_ROUTE_ATTEMPTS = 2;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final OpenRouteServiceClient openRouteServiceClient;
    private final AtomicBoolean missingConfigurationLogged =
            new AtomicBoolean(false);

    AbstractOpenRouteCalculator(OpenRouteServiceClient openRouteServiceClient) {
        this.openRouteServiceClient = openRouteServiceClient;
    }

    @Override
    public RouteCalculation calculate(List<RouteCoordinate> coordinates) {
        RouteCalculation fallback = estimate(coordinates);
        if (coordinates == null || coordinates.size() < 2) {
            return fallback;
        }

        if (openRouteServiceClient != null && openRouteServiceClient.isConfigured()) {
            RuntimeException lastFailure = null;

            for (int attempt = 1; attempt <= MAX_ACTUAL_ROUTE_ATTEMPTS; attempt++) {
                try {
                    RouteMatrixResult result = openRouteServiceClient.calculateMatrix(
                            openRouteProfile(),
                            coordinates
                    );

                    /*
                     * ORS가 HTTP 성공으로 응답하더라도 모든 실제 이동 셀이 null인 경우가 있다.
                     * 이 값을 바로 반환하면 첫 코스 DAY 1 전체가 예상값으로 확정되므로 한 번 더 조회한다.
                     */
                    if (!hasUsableActualRoute(result, coordinates.size())) {
                        throw new IllegalStateException(
                                "OpenRouteService가 실제 이동 가능한 경로 셀을 반환하지 않았습니다."
                        );
                    }

                    if (attempt > 1) {
                        log.info(
                                "{} 경로 API 재시도 성공: attempt={}/{}",
                                supportedMode(),
                                attempt,
                                MAX_ACTUAL_ROUTE_ATTEMPTS
                        );
                    }
                    return mergeActualAndFallback(result, fallback);
                } catch (RuntimeException exception) {
                    lastFailure = exception;
                    boolean retryable = attempt < MAX_ACTUAL_ROUTE_ATTEMPTS
                            && isRetryable(exception);

                    if (!retryable) {
                        break;
                    }

                    log.warn(
                            "{} 경로 API 첫 호출 실패로 한 번 재시도합니다: {}",
                            supportedMode(),
                            exception.getMessage()
                    );
                }
            }

            log.warn(
                    "{} 경로 API 호출 실패로 임시 추정값을 사용합니다: {}",
                    supportedMode(),
                    lastFailure == null ? "원인을 확인할 수 없습니다." : lastFailure.getMessage()
            );
        } else if (openRouteServiceClient != null
                && missingConfigurationLogged.compareAndSet(false, true)) {
            log.warn(
                    "{} 실제 경로 조회가 비활성화되어 있습니다. "
                            + "IntelliJ 백엔드 실행 환경변수 "
                            + "OPENROUTESERVICE_API_KEY를 확인해주세요.",
                    supportedMode()
            );
        }

        return fallback;
    }

    /** 추천 후보 정렬 단계에서는 외부 API를 호출하지 않고 전용 추정 행렬만 만든다. */
    @Override
    public RouteCalculation estimate(List<RouteCoordinate> coordinates) {
        return RouteEstimationSupport.estimate(
                coordinates,
                fallbackRouteDistanceFactor(),
                fallbackTravelMinutesEstimator()
        );
    }

    /** ORS 응답에 자기 자신을 제외한 실제 거리·시간 셀이 하나라도 있는지 확인한다. */
    private boolean hasUsableActualRoute(
            RouteMatrixResult result,
            int expectedSize
    ) {
        if (result == null || expectedSize < 2) {
            return false;
        }

        for (int fromIndex = 0; fromIndex < expectedSize; fromIndex++) {
            for (int toIndex = 0; toIndex < expectedSize; toIndex++) {
                if (fromIndex == toIndex) {
                    continue;
                }

                double distanceKm = result.getDistanceKm(fromIndex, toIndex);
                double travelMinutes = result.getTravelTimeMinutes(fromIndex, toIndex);
                if (Double.isFinite(distanceKm)
                        && Double.isFinite(travelMinutes)
                        && distanceKm >= 0.0
                        && travelMinutes >= 0.0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 429·5xx·통신 오류는 한 번 재시도하고, 인증 오류와 실제 일일 예산 소진은 즉시 추정값으로 전환한다. */
    private boolean isRetryable(RuntimeException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException responseException) {
                int status = responseException.getStatusCode().value();
                return status == 429 || status >= 500;
            }

            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            if (message.contains("일일 호출 예산")
                    || message.contains("인증 오류")
                    || message.contains("HTTP 401")
                    || message.contains("HTTP 403")) {
                return false;
            }
            if (message.contains("HTTP 429")) {
                return true;
            }
        }

        // 연결 초기화·DNS·읽기 제한시간과 같이 일시적인 통신 실패는 한 번 재시도한다.
        return true;
    }

    /** ORS의 null 셀만 추정값으로 남기고 정상 셀은 실제 경로로 교체한다. */
    private RouteCalculation mergeActualAndFallback(
            RouteMatrixResult actual,
            RouteCalculation fallback
    ) {
        double[][] distancesKm = fallback.distancesKm();
        double[][] travelTimesMinutes = fallback.travelTimesMinutes();
        boolean[][] estimatedPairs = fallback.estimatedPairs();

        for (int fromIndex = 0; fromIndex < distancesKm.length; fromIndex++) {
            for (int toIndex = 0; toIndex < distancesKm.length; toIndex++) {
                double actualDistance = actual.getDistanceKm(fromIndex, toIndex);
                double actualMinutes = actual.getTravelTimeMinutes(fromIndex, toIndex);
                if (!Double.isFinite(actualDistance)
                        || !Double.isFinite(actualMinutes)
                        || actualDistance < 0.0
                        || actualMinutes < 0.0) {
                    continue;
                }

                distancesKm[fromIndex][toIndex] = actualDistance;
                travelTimesMinutes[fromIndex][toIndex] = actualMinutes;
                estimatedPairs[fromIndex][toIndex] = false;
            }
        }

        return new RouteCalculation(
                distancesKm,
                travelTimesMinutes,
                estimatedPairs
        );
    }

    protected abstract String openRouteProfile();

    protected abstract double fallbackRouteDistanceFactor();

    protected abstract DoubleUnaryOperator fallbackTravelMinutesEstimator();

    @Override
    public abstract TransportMode supportedMode();
}
