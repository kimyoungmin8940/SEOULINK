package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/** 외부 경로 API를 사용할 수 없을 때만 쓰는 명시적인 임시 추정 계산기이다. */
final class RouteEstimationSupport {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private RouteEstimationSupport() {
    }

    static RouteCalculation estimate(
            List<RouteCoordinate> coordinates,
            double routeDistanceFactor,
            DoubleUnaryOperator travelMinutesEstimator
    ) {
        int size = coordinates.size();
        double[][] distancesKm = new double[size][size];
        double[][] travelTimesMinutes = new double[size][size];

        for (int fromIndex = 0; fromIndex < size; fromIndex++) {
            for (int toIndex = 0; toIndex < size; toIndex++) {
                if (fromIndex == toIndex) {
                    continue;
                }

                double straightDistanceKm = haversine(
                        coordinates.get(fromIndex),
                        coordinates.get(toIndex)
                );
                double routeDistanceKm = straightDistanceKm * routeDistanceFactor;
                distancesKm[fromIndex][toIndex] = routeDistanceKm;
                travelTimesMinutes[fromIndex][toIndex] =
                        travelMinutesEstimator.applyAsDouble(routeDistanceKm);
            }
        }

        return new RouteCalculation(distancesKm, travelTimesMinutes, true);
    }

    private static double haversine(RouteCoordinate from, RouteCoordinate to) {
        double startLatitudeRadians = Math.toRadians(from.latitude());
        double endLatitudeRadians = Math.toRadians(to.latitude());
        double latitudeDifference = Math.toRadians(to.latitude() - from.latitude());
        double longitudeDifference = Math.toRadians(to.longitude() - from.longitude());

        double haversine = Math.pow(Math.sin(latitudeDifference / 2), 2)
                + Math.cos(startLatitudeRadians)
                * Math.cos(endLatitudeRadians)
                * Math.pow(Math.sin(longitudeDifference / 2), 2);
        double normalized = Math.max(0.0, Math.min(1.0, haversine));
        double centralAngle = 2 * Math.atan2(
                Math.sqrt(normalized),
                Math.sqrt(1 - normalized)
        );
        return EARTH_RADIUS_KM * centralAngle;
    }
}
