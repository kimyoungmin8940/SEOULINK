package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteMatrixResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleUnaryOperator;

/** 도보·자동차의 OpenRouteService 호출과 장애 시 추정 계산을 공통 처리한다. */
abstract class AbstractOpenRouteCalculator implements RouteCalculator {

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

        if (openRouteServiceClient != null && openRouteServiceClient.isConfigured()) {
            try {
                RouteMatrixResult result = openRouteServiceClient.calculateMatrix(
                        openRouteProfile(),
                        coordinates
                );
                return mergeActualAndFallback(result, fallback);
            } catch (RuntimeException exception) {
                log.warn(
                        "{} 경로 API 호출 실패로 임시 추정값을 사용합니다: {}",
                        supportedMode(),
                        exception.getMessage()
                );
            }
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
