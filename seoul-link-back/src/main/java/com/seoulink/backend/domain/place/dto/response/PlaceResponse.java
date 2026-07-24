package com.seoulink.backend.domain.place.dto.response;

import com.seoulink.backend.domain.place.entity.Place;
import lombok.Getter;

@Getter
public class PlaceResponse {

    private final Long placeId;
    private final String apiProvider;
    private final String apiPlaceId;
    private final Long contentId;
    private final String name;
    private final String category;
    private final String apiCategory;
    private final String region;
    private final String address;
    private final String roadAddress;
    private final Double latitude;
    private final Double longitude;
    private final String phone;
    private final String placeUrl;
    private final Double rating;
    private final Integer reviewCount;
    private final String description;
    private final String imageUrl;
    private final String tagHistory;
    private final String tagModern;
    private final String tagBudget;
    private final String tagLuxury;
    private final String tagStable;
    private final String tagDopamine;
    private final String tagRelax;
    private final String tagPacked;
    private final String themePalaceCultureYn;
    private final String themeNatureHangangYn;
    private final String themeDateYn;
    private final String themeFoodTourYn;
    private final String themeCafeTourYn;
    private final String themeShoppingHotplaceYn;
    private final String themeNightViewYn;
    private final String themeHotelStayYn;
    private final String isActive;
    private final Integer avgStayMinutes;

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
        this.themePalaceCultureYn = place.getThemePalaceCultureYn();
        this.themeNatureHangangYn = place.getThemeNatureHangangYn();
        this.themeDateYn = place.getThemeDateYn();
        this.themeFoodTourYn = place.getThemeFoodTourYn();
        this.themeCafeTourYn = place.getThemeCafeTourYn();
        this.themeShoppingHotplaceYn = place.getThemeShoppingHotplaceYn();
        this.themeNightViewYn = place.getThemeNightViewYn();
        this.themeHotelStayYn = place.getThemeHotelStayYn();
        this.isActive = place.getIsActive();
        this.avgStayMinutes = place.getAvgStayMinutes();
    }
}
