package com.seoulink.backend.domain.course.dto.request;

public record CourseBuilderPlaceRequest(
        Long placeId,
        Integer dayNo,
        Integer placeOrder,
        String memo,
        String visitTime,
        Integer stayMinutes
) {
}