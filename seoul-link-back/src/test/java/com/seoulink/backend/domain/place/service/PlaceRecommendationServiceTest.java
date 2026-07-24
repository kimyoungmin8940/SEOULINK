package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationListResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceCandidatePoolResponse;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.exception.InvalidTravelCodeException;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void normalizesDisplayedScoresWithoutChangingCompanionRanking() {
        Place genericPlace = place(1L, "일반 관광지", "TOUR", true);
        Place datePlace = place(2L, "데이트 관광지", "TOUR", true);
        datePlace.setThemeDateYn("Y");
        when(placeRepository.findByIsActive("Y"))
                .thenReturn(List.of(genericPlace, datePlace));

        PlaceRecommendationListResponse response =
                placeRecommendationService.recommend(
                        "ATBSP",
                        null,
                        2,
                        null,
                        1,
                        "COUPLE"
                );

        assertEquals(2L, response.getRecommendedPlaces().get(0).getPlaceId());
        assertTrue(response.getRecommendedPlaces().stream()
                .allMatch(place -> place.getRecommendationScore() >= 70.0
                        && place.getRecommendationScore() <= 95.0));
        assertEquals(
                95.0,
                response.getRecommendedPlaces().get(0).getRecommendationScore(),
                0.000001
        );
        assertEquals(
                70.0,
                response.getRecommendedPlaces().get(1).getRecommendationScore(),
                0.000001
        );
    }

    @Test
    void returnsPackedPoolWithFortyEightUsableCandidates() {
        List<Place> places = new ArrayList<>();
        places.addAll(places(1L, 30, "TOUR"));
        places.addAll(places(101L, 20, "RESTAURANT"));
        places.addAll(places(201L, 12, "CAFE"));
        places.addAll(places(301L, 8, "HOTEL"));

        // 최대 검토 여유분 6개가 좌표 누락을 흡수하는지 확인한다.
        places.get(0).setLatitude(null);
        places.get(1).setLatitude(null);
        places.get(2).setLatitude(null);
        places.get(30).setLongitude(null);
        places.get(31).setLongitude(null);
        places.get(50).setLatitude(null);

        when(placeRepository.findByIsActive("Y")).thenReturn(places);

        PlaceCandidatePoolResponse response =
                placeRecommendationService.recommendCandidatePool(
                        "ATBSP",
                        null,
                        "P",
                        "FRIENDS",
                        Set.of()
                );

        assertEquals(48, response.getTargetCandidateCount());
        assertEquals(54, response.getMaxLookupCount());
        assertEquals(24, response.getCandidatesForCategory("TOUR").size());
        assertEquals(16, response.getCandidatesForCategory("RESTAURANT").size());
        assertEquals(8, response.getCandidatesForCategory("CAFE").size());
        assertEquals(48, response.getCandidates().size());
        assertEquals(6, response.getHotelCandidates().size());
        assertTrue(response.getCandidates().stream()
                .allMatch(candidate ->
                        candidate.getAlternativeCandidates().isEmpty()));
    }

    @Test
    void returnsRelaxedPoolAndKeepsExcludedPlacesOnlyAsFallback() {
        List<Place> places = new ArrayList<>();
        places.addAll(places(1L, 22, "TOUR"));
        places.addAll(places(101L, 12, "RESTAURANT"));
        places.addAll(places(201L, 12, "CAFE"));
        places.addAll(places(301L, 8, "HOTEL"));
        places.add(place(999L, "잘못된 카테고리", "OTHER", true));

        Set<Long> excludedPlaceIds = Set.of(1L, 101L, 201L, 301L);
        when(placeRepository.findByIsActive("Y")).thenReturn(places);

        PlaceCandidatePoolResponse response =
                placeRecommendationService.recommendCandidatePool(
                        "ATBSR",
                        null,
                        "R",
                        "COUPLE",
                        excludedPlaceIds
                );

        assertEquals(32, response.getTargetCandidateCount());
        assertEquals(36, response.getMaxLookupCount());
        assertEquals(16, response.getCandidatesForCategory("TOUR").size());
        assertEquals(8, response.getCandidatesForCategory("RESTAURANT").size());
        assertEquals(8, response.getCandidatesForCategory("CAFE").size());
        assertFalse(response.getCandidates().stream()
                .anyMatch(candidate ->
                        excludedPlaceIds.contains(candidate.getPlaceId())));
        assertTrue(response.getFallbackCandidatesByCategory()
                .values()
                .stream()
                .flatMap(List::stream)
                .anyMatch(candidate -> candidate.getPlaceId().equals(1L)));
        assertFalse(response.getHotelCandidates().stream()
                .anyMatch(candidate -> candidate.getPlaceId().equals(301L)));
        assertTrue(response.getFallbackHotelCandidates().stream()
                .anyMatch(candidate -> candidate.getPlaceId().equals(301L)));
    }

    @Test
    void rejectsScheduleTypeThatDoesNotMatchTravelCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> placeRecommendationService.recommendCandidatePool(
                        "ATBSP",
                        null,
                        "R",
                        null,
                        Set.of()
                )
        );
        verifyNoInteractions(placeRepository);
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
            default -> {
                // 지원하지 않는 카테고리가 후보 풀에서 제외되는지 검증할 때 사용한다.
            }
        }
        return place;
    }

    private List<Place> places(
            long firstId,
            int count,
            String category
    ) {
        List<Place> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            long placeId = firstId + index;
            result.add(place(
                    placeId,
                    category + " " + placeId,
                    category,
                    true
            ));
        }
        return result;
    }
}
