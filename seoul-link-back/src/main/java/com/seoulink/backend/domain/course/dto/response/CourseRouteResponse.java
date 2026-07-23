package com.seoulink.backend.domain.course.dto.response;

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

            // 차량 기준 거리/시간
            Integer distanceMeter,
            Integer durationSecond,
            Integer durationMinute,

            // 도보 기준 거리/시간
            Integer walkingDistanceMeter,
            Integer walkingDurationSecond,
            Integer walkingDurationMinute,

            // 지도에는 차량 기준 경로 좌표를 전달
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
