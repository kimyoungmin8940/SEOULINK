package com.seoulink.backend.coursebuilder.dto;

public record CourseBuilderPlaceDto(
        Long placeId,
        String name,
        String category,
        String region,
        String address,
        Double latitude,
        Double longitude,
        Double rating,
        Integer reviewCount,
        String imageUrl,
        String tagHistory,
        String tagModern,
        String tagBudget,
        String tagLuxury,
        String tagStable,
        String tagDopamine,
        String tagRelax,
        String tagPacked
) {
}
