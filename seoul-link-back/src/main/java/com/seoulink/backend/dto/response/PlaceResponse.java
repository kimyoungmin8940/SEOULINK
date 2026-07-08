package com.seoulink.backend.dto.response;

import com.seoulink.backend.entity.Place;
import lombok.Getter;

@Getter
public class PlaceResponse {

    private Long placeId;
    private String apiProvider;
    private String apiPlaceId;
    private Long contentId;

    private String name;
    private String category;
    private String apiCategory;

    private String region;
    private String address;
    private String roadAddress;

    private Double latitude;
    private Double longitude;

    private String phone;
    private String placeUrl;

    private Double rating;
    private Integer reviewCount;

    private String description;
    private String imageUrl;

    private String tagHistory;
    private String tagModern;
    private String tagBudget;
    private String tagLuxury;
    private String tagStable;
    private String tagDopamine;
    private String tagRelax;
    private String tagPacked;

    private String isActive;

    public PlaceResponse(Place place) {
        this.placeId = place.getPlaceId();
        this.apiProvider = place.getApiProvider();
        this.apiPlaceId = place.getApiPlaceId();
        this.contentId = place.getContentId();

        this.name = place.getName();
        this.category = place.getCategory();
        this.apiCategory = place.getApiCategory();

        this.region = place.getRegion();
        this.address = place.getAddress();
        this.roadAddress = place.getRoadAddress();

        this.latitude = place.getLatitude();
        this.longitude = place.getLongitude();

        this.phone = place.getPhone();
        this.placeUrl = place.getPlaceUrl();

        this.rating = place.getRating();
        this.reviewCount = place.getReviewCount();

        this.description = place.getDescription();
        this.imageUrl = place.getImageUrl();

        this.tagHistory = place.getTagHistory();
        this.tagModern = place.getTagModern();
        this.tagBudget = place.getTagBudget();
        this.tagLuxury = place.getTagLuxury();
        this.tagStable = place.getTagStable();
        this.tagDopamine = place.getTagDopamine();
        this.tagRelax = place.getTagRelax();
        this.tagPacked = place.getTagPacked();

        this.isActive = place.getIsActive();
    }
}