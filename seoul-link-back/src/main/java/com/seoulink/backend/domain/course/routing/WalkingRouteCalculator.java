package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import org.springframework.stereotype.Component;

import java.util.function.DoubleUnaryOperator;

/** 도보는 OpenRouteService의 {@code foot-walking} 프로필을 사용한다. */
@Component
public class WalkingRouteCalculator extends AbstractOpenRouteCalculator {

    private static final double ROUTE_DISTANCE_FACTOR = 1.15;
    private static final double WALKING_SPEED_KM_PER_HOUR = 4.5;

    public WalkingRouteCalculator(OpenRouteServiceClient openRouteServiceClient) {
        super(openRouteServiceClient);
    }

    @Override
    public TransportMode supportedMode() {
        return TransportMode.WALKING;
    }

    @Override
    protected String openRouteProfile() {
        return "foot-walking";
    }

    @Override
    protected double fallbackRouteDistanceFactor() {
        return ROUTE_DISTANCE_FACTOR;
    }

    @Override
    protected DoubleUnaryOperator fallbackTravelMinutesEstimator() {
        return distanceKm -> distanceKm / WALKING_SPEED_KM_PER_HOUR * 60.0;
    }
}
