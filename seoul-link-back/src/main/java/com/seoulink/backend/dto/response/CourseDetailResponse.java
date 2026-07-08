package com.seoulink.backend.dto.response;

import com.seoulink.backend.entity.CourseDetail;
import com.seoulink.backend.entity.Place;
import lombok.Getter;

@Getter
public class CourseDetailResponse {

    private Long detailId;
    private Long placeId;

    private Integer dayNo;
    private Integer placeOrder;

    private String memo;
    private String visitTime;
    private Integer stayMinutes;

    private String placeName;
    private String category;
    private String region;
    private String address;
    private String roadAddress;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String placeUrl;
    private String imageUrl;

    public CourseDetailResponse(CourseDetail detail, Place place) {
        this.detailId = detail.getDetailId();
        this.placeId = detail.getPlaceId();

        this.dayNo = detail.getDayNo();
        this.placeOrder = detail.getPlaceOrder();

        this.memo = detail.getMemo();
        this.visitTime = detail.getVisitTime();
        this.stayMinutes = detail.getStayMinutes();

        if (place != null) {
            this.placeName = place.getName();
            this.category = place.getCategory();
            this.region = place.getRegion();
            this.address = place.getAddress();
            this.roadAddress = place.getRoadAddress();
            this.latitude = place.getLatitude();
            this.longitude = place.getLongitude();
            this.phone = place.getPhone();
            this.placeUrl = place.getPlaceUrl();
            this.imageUrl = place.getImageUrl();
        }
    }
}