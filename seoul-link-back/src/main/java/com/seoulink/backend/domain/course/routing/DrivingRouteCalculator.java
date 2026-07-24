package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import org.springframework.stereotype.Component;

import java.util.function.DoubleUnaryOperator;

/** 자동차는 OpenRouteService의 {@code driving-car} 프로필을 사용한다. */
@Component
public class DrivingRouteCalculator extends AbstractOpenRouteCalculator {

    private static final double ROUTE_DISTANCE_FACTOR = 1.25;
    private static final double URBAN_DRIVING_SPEED_KM_PER_HOUR = 25.0;
    private static final double FIXED_TRAFFIC_AND_PARKING_MINUTES = 3.0;

    public DrivingRouteCalculator(OpenRouteServiceClient openRouteServiceClient) {
        super(openRouteServiceClient);
    }

    @Override
    public TransportMode supportedMode() {
        return TransportMode.DRIVING;
    }

    @Override
    protected String openRouteProfile() {
        return "driving-car";
    }

    @Override
    protected double fallbackRouteDistanceFactor() {
        return ROUTE_DISTANCE_FACTOR;
    }

    @Override
    protected DoubleUnaryOperator fallbackTravelMinutesEstimator() {
        return distanceKm -> distanceKm == 0.0
                ? 0.0
                : distanceKm / URBAN_DRIVING_SPEED_KM_PER_HOUR * 60.0
                + FIXED_TRAFFIC_AND_PARKING_MINUTES;
    }
}
