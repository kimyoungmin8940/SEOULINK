package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.entity.Place;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceTaggingServiceTest {

    private final PlaceTaggingService placeTaggingService = new PlaceTaggingService();

    @Test
    void genericTouristAttractionIsNotAutomaticallyClassifiedAsHistory() {
        Place place = place("서울광장", "관광명소");

        placeTaggingService.applyTags(place);

        assertEquals("TOUR", place.getCategory());
        assertEquals("N", place.getThemePalaceCultureYn());
        assertEquals("N", place.getTagHistory());
        assertEquals("Y", place.getTagPacked());
    }

    @Test
    void cafeSubcategoryWinsOverFoodParentCategory() {
        Place place = place("테스트 카페", "음식점 > 카페 > 커피전문점");

        placeTaggingService.applyTags(place);

        assertEquals("CAFE", place.getCategory());
        assertEquals("Y", place.getThemeCafeTourYn());
        assertEquals("N", place.getThemeFoodTourYn());
        assertEquals("Y", place.getTagPacked());
    }

    @Test
    void shortNatureAttractionGetsPackedAndActivityTags() {
        Place place = place("반포한강공원", "관광명소");

        placeTaggingService.applyTags(place);

        assertEquals("Y", place.getThemeNatureHangangYn());
        assertEquals("Y", place.getThemeDateYn());
        assertEquals("Y", place.getTagRelax());
        assertEquals("Y", place.getTagDopamine());
        assertEquals("Y", place.getTagPacked());
        assertEquals(90, place.getAvgStayMinutes());
    }

    @Test
    void hotelIsNotMarkedAsPacked() {
        Place place = place("서울 테스트 호텔", "숙박 > 호텔");

        placeTaggingService.applyTags(place);

        assertEquals("HOTEL", place.getCategory());
        assertEquals("Y", place.getThemeHotelStayYn());
        assertEquals("N", place.getTagPacked());
        assertEquals(720, place.getAvgStayMinutes());
    }

    @Test
    void yongsanAddressDoesNotMakeCafeANaturePlace() {
        Place place = place("용산역 카페", "음식점 > 카페");
        place.setAddress("서울 용산구 한강대로 100");
        place.setRoadAddress("서울 용산구 한강대로 100");
        place.setRegion("용산구");

        placeTaggingService.applyTags(place);

        assertEquals("CAFE", place.getCategory());
        assertEquals("N", place.getThemeNatureHangangYn());
        assertEquals("N", place.getTagRelax());
    }

    private Place place(String name, String apiCategory) {
        Place place = new Place();
        place.setName(name);
        place.setApiCategory(apiCategory);
        place.setCategory("TOUR");
        place.setAddress("서울특별시");
        place.setRoadAddress("서울특별시");
        place.setRegion("서울");
        return place;
    }
}
