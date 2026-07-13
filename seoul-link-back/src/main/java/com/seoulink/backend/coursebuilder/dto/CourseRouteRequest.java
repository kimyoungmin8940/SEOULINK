package com.seoulink.backend.coursebuilder.dto;

import java.util.List;

public record CourseRouteRequest(
        String mode,
        List<RoutePlaceRequest> places
) {
    public record RoutePlaceRequest(
            String clientPlaceId,
            String name,
            Integer dayNo,
            Integer placeOrder,
            Double latitude,
            Double longitude
    ) {
    }
}
