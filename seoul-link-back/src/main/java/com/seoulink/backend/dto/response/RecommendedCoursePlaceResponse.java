package com.seoulink.backend.dto.response;

import com.seoulink.backend.entity.Place;
import lombok.Getter;

@Getter
public class RecommendedCoursePlaceResponse {
    private Long placeId;
    private String name;
    private String category;
    private String region;
    private String address;
    private Double latitude;
    private Double longitude;
    private Integer dayNo;
    private Integer placeOrder;
    private String visitTime;
    private Integer stayMinutes;

    public RecommendedCoursePlaceResponse(Place place, Integer dayNo, Integer placeOrder, String visitTime, Integer stayMinutes) {
        this.placeId = place.getPlaceId();
        this.name = place.getName();
        this.category = place.getCategory();
        this.region = place.getRegion();
        this.address = place.getAddress();
        this.latitude = place.getLatitude();
        this.longitude = place.getLongitude();
        this.dayNo = dayNo;
        this.placeOrder = placeOrder;
        this.visitTime = visitTime;
        this.stayMinutes = stayMinutes;
    }
}
