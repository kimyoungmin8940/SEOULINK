package com.seoulink.backend.coursebuilder.dto;

import java.util.List;

public record CourseRouteResponse(
        List<RouteSegmentResponse> segments
) {
    public record RouteSegmentResponse(
            String fromClientPlaceId,
            String toClientPlaceId,
            String fromPlaceName,
            String toPlaceName,
            Integer fromIndex,
            Integer toIndex,
            Integer dayNo,
            Integer distanceMeter,
            Integer durationSecond,
            Integer durationMinute,
            List<RoutePointResponse> routePoints,
            String statusMessage
    ) {
    }

    public record RoutePointResponse(
            Double latitude,
            Double longitude
    ) {
    }
}
