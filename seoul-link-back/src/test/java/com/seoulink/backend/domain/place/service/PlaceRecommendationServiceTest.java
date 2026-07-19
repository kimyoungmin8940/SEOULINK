package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationListResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.exception.InvalidTravelCodeException;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlaceRecommendationServiceTest {

    private PlaceRepository placeRepository;
    private PlaceRecommendationService placeRecommendationService;

    @BeforeEach
    void setUp() {
        placeRepository = mock(PlaceRepository.class);
        placeRecommendationService = new PlaceRecommendationService(placeRepository);
    }

    @Test
    void rejectsCodeWhoseLettersAreInWrongPositions() {
        assertThrows(
                InvalidTravelCodeException.class,
                () -> placeRecommendationService.recommend("AAAAA", 10, 3)
        );
        verifyNoInteractions(placeRepository);
    }

    @Test
    void excludesHighlyRatedPlaceWhenNoPreferenceTagMatches() {
        Place matched = place(1L, "역사 장소", "TOUR", true);
        Place unmatched = place(2L, "인기 장소", "TOUR", false);
        unmatched.setRating(5.0);
        unmatched.setReviewCount(100_000);

        when(placeRepository.findByIsActive("Y")).thenReturn(List.of(unmatched, matched));

        PlaceRecommendationListResponse response = placeRecommendationService.recommend("ATBSP", 10, 3);

        assertEquals("ATBSP", response.getTravelCode());
        assertEquals(1, response.getRecommendedPlaces().size());
        assertEquals(matched.getPlaceId(), response.getRecommendedPlaces().get(0).getPlaceId());
    }

    @Test
    void returnsCategoryLeadersAndSameCategoryAlternatives() {
        List<Place> places = List.of(
                place(1L, "관광지 1", "TOUR", true),
                place(2L, "관광지 2", "TOUR", true),
                place(3L, "식당 1", "RESTAURANT", true),
                place(4L, "식당 2", "RESTAURANT", true),
                place(5L, "카페 1", "CAFE", true),
                place(6L, "카페 2", "CAFE", true),
                place(7L, "숙소 1", "HOTEL", true),
                place(8L, "숙소 2", "HOTEL", true)
        );
        when(placeRepository.findByIsActive("Y")).thenReturn(places);

        PlaceRecommendationListResponse response = placeRecommendationService.recommend(" atbsp ", 4, 1);

        Set<String> categories = response.getRecommendedPlaces().stream()
                .map(PlaceRecommendationResponse::getCategory)
                .collect(Collectors.toSet());

        assertEquals("ATBSP", response.getTravelCode());
        assertEquals(Set.of("TOUR", "RESTAURANT", "CAFE", "HOTEL"), categories);
        assertTrue(response.getRecommendedPlaces().stream()
                .allMatch(recommended -> recommended.getAlternativeCandidates().size() == 1));
        assertTrue(response.getRecommendedPlaces().stream()
                .allMatch(recommended -> recommended.getAlternativeCandidates().stream()
                        .allMatch(alternative -> alternative.getCategory().equals(recommended.getCategory()))));

        List<Long> alternativeIds = response.getRecommendedPlaces().stream()
                .flatMap(recommended -> recommended.getAlternativeCandidates().stream())
                .map(alternative -> alternative.getPlaceId())
                .toList();
        assertEquals(alternativeIds.size(), alternativeIds.stream().distinct().count());
    }

    @Test
    void returnsRequestedCandidateCountPerCategoryWithinRegion() {
        List<Place> places = List.of(
                place(1L, "관광지 1", "TOUR", true),
                place(2L, "관광지 2", "TOUR", true),
                place(3L, "식당 1", "RESTAURANT", true),
                place(4L, "식당 2", "RESTAURANT", true),
                place(5L, "카페 1", "CAFE", true),
                place(6L, "카페 2", "CAFE", true),
                place(7L, "숙소 1", "HOTEL", true),
                place(8L, "숙소 2", "HOTEL", true)
        );
        when(placeRepository.findByRegionContainingAndIsActive("성동구", "Y"))
                .thenReturn(places);

        PlaceRecommendationListResponse response = placeRecommendationService.recommend(
                "ATBSP",
                "서울특별시 성동구",
                null,
                2,
                1
        );

        assertEquals(8, response.getRecommendedPlaces().size());
        for (String category : Set.of("TOUR", "RESTAURANT", "CAFE", "HOTEL")) {
            long count = response.getRecommendedPlaces().stream()
                    .filter(place -> category.equals(place.getCategory()))
                    .count();
            assertEquals(2, count);
        }
        verify(placeRepository).findByRegionContainingAndIsActive("성동구", "Y");
    }

    private Place place(Long id, String name, String category, boolean matchesHistory) {
        Place place = new Place();
        place.setPlaceId(id);
        place.setName(name);
        place.setCategory(category);
        place.setLatitude(37.5 + id / 10_000.0);
        place.setLongitude(127.0 + id / 10_000.0);
        place.setRating(0.0);
        place.setReviewCount(0);
        place.setIsActive("Y");
        place.setTagHistory(matchesHistory ? "Y" : "N");

        switch (category) {
            case "TOUR" -> place.setThemePalaceCultureYn("Y");
            case "RESTAURANT" -> place.setThemeFoodTourYn("Y");
            case "CAFE" -> place.setThemeCafeTourYn("Y");
            case "HOTEL" -> place.setThemeHotelStayYn("Y");
            default -> throw new IllegalArgumentException("지원하지 않는 테스트 카테고리입니다.");
        }
        return place;
    }
}
