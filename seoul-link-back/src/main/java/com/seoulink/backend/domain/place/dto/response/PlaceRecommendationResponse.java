package com.seoulink.backend.domain.place.dto.response;

import com.seoulink.backend.domain.place.entity.Place;
import lombok.Getter;

import java.util.List;

@Getter
public class PlaceRecommendationResponse {

    private final Long placeId;
    private final String placeName;
    private final String category;
    private final Double recommendationScore;
    private final Double latitude;
    private final Double longitude;
    private final String themePalaceCultureYn;
    private final String themeNatureHangangYn;
    private final String themeDateYn;
    private final String themeFoodTourYn;
    private final String themeCafeTourYn;
    private final String themeShoppingHotplaceYn;
    private final String themeNightViewYn;
    private final String themeHotelStayYn;
    private final String region;
    private final String imageUrl;
    private final List<PlaceAlternativeResponse> alternativeCandidates;

    public PlaceRecommendationResponse(
            Place place,
            Double recommendationScore,
            List<PlaceAlternativeResponse> alternativeCandidates
    ) {
        this.placeId = place.getPlaceId();
        this.placeName = place.getName();
        this.category = place.getCategory();
        this.recommendationScore = recommendationScore;
        this.latitude = place.getLatitude();
        this.longitude = place.getLongitude();
        this.themePalaceCultureYn = place.getThemePalaceCultureYn();
        this.themeNatureHangangYn = place.getThemeNatureHangangYn();
        this.themeDateYn = place.getThemeDateYn();
        this.themeFoodTourYn = place.getThemeFoodTourYn();
        this.themeCafeTourYn = place.getThemeCafeTourYn();
        this.themeShoppingHotplaceYn = place.getThemeShoppingHotplaceYn();
        this.themeNightViewYn = place.getThemeNightViewYn();
        this.themeHotelStayYn = place.getThemeHotelStayYn();
        this.region = place.getRegion();
        this.imageUrl = place.getImageUrl();
        this.alternativeCandidates = alternativeCandidates;
    }
}
