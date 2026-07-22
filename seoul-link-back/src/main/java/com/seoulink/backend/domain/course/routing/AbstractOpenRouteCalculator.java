package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteMatrixResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/** 도보·자동차의 OpenRouteService 호출과 장애 시 추정 계산을 공통 처리한다. */
abstract class AbstractOpenRouteCalculator implements RouteCalculator {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final OpenRouteServiceClient openRouteServiceClient;

    AbstractOpenRouteCalculator(OpenRouteServiceClient openRouteServiceClient) {
        this.openRouteServiceClient = openRouteServiceClient;
    }

    @Override
    public RouteCalculation calculate(List<RouteCoordinate> coordinates) {
        if (openRouteServiceClient != null && openRouteServiceClient.isConfigured()) {
            try {
                RouteMatrixResult result = openRouteServiceClient.calculateMatrix(
                        openRouteProfile(),
                        coordinates
                );
                return new RouteCalculation(
                        result.distancesKm(),
                        result.travelTimesMinutes(),
                        false
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "{} 경로 API 호출 실패로 임시 추정값을 사용합니다: {}",
                        supportedMode(),
                        exception.getMessage()
                );
            }
        }

        return RouteEstimationSupport.estimate(
                coordinates,
                fallbackRouteDistanceFactor(),
                fallbackTravelMinutesEstimator()
        );
    }

    protected abstract String openRouteProfile();

    protected abstract double fallbackRouteDistanceFactor();

    protected abstract DoubleUnaryOperator fallbackTravelMinutesEstimator();

    @Override
    public abstract TransportMode supportedMode();
}
