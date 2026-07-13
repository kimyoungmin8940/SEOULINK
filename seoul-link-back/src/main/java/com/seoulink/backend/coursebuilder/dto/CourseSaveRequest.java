package com.seoulink.backend.coursebuilder.dto;

import java.util.List;

public record CourseSaveRequest(
        Long memberId,
        Long resultId,
        Long paymentId,

        String title,
        String description,

        String travelCode,
        String courseType,
        String region,
        String isPublic,

        List<PlaceRequest> places
) {
    public record PlaceRequest(
            Long placeId,

            String apiProvider,
            String apiPlaceId,
            Long contentId,

            String name,
            String category,
            String apiCategory,

            String region,
            String address,
            String roadAddress,

            Double latitude,
            Double longitude,

            String phone,
            String placeUrl,

            Double rating,
            Integer reviewCount,

            String description,
            String imageUrl,

            String sourceType,
            String recommendYn,
            String approvalStatus,

            Integer dayNo,
            Integer placeOrder,
            String memo,
            String visitTime,
            Integer stayMinutes,

            Integer moveDistanceM,
            Integer moveDurationMin
    ) {
    }
}
