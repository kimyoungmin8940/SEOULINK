package com.seoulink.backend.domain.course.dto.request;

import java.util.List;

/**
 * 지도 기반 직접 코스 만들기 전용 저장 요청 DTO입니다.
 * 기존 추천 코스 저장용 {@link CourseSaveRequest}와 분리해 지도 API에서 받은
 * 신규 장소의 메타데이터와 이동 정보를 함께 저장합니다.
 */
public record CourseBuilderSaveRequest(
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
            String visitDate,
            Integer stayMinutes,
            Integer moveDistanceM,
            Integer moveDurationMin
    ) {
    }
}
