package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDraftResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceCandidatePoolResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.service.PlaceRecommendationService;
import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import com.seoulink.backend.domain.survey.repository.SurveyResultRepository;
import com.seoulink.backend.domain.survey.repository.TravelSurveyRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseDraftServiceTest {

    @Test
    void sharesExpandedCandidatePoolAcrossDailyPlans() {
        TravelSurveyRepository travelSurveyRepository =
                mock(TravelSurveyRepository.class);
        SurveyResultRepository surveyResultRepository =
                mock(SurveyResultRepository.class);
        PlaceRecommendationService placeRecommendationService =
                mock(PlaceRecommendationService.class);
        TravelSurvey survey = mock(TravelSurvey.class);
        SurveyResult surveyResult = mock(SurveyResult.class);

        when(survey.getSurveyId()).thenReturn(1L);
        when(survey.getRegion()).thenReturn("서울");
        when(survey.getStartDate()).thenReturn(LocalDate.of(2026, 7, 20));
        when(survey.getEndDate()).thenReturn(LocalDate.of(2026, 7, 21));
        when(survey.getCompanionType()).thenReturn("COUPLE");
        when(survey.getTransportType()).thenReturn("WALKING");
        when(surveyResult.getResultId()).thenReturn(101L);
        when(surveyResult.getTravelCode()).thenReturn("ATLSP");
        when(travelSurveyRepository.findById(1L))
                .thenReturn(Optional.of(survey));
        when(surveyResultRepository.findBySurveyId(1L))
                .thenReturn(Optional.of(surveyResult));

        PlaceCandidatePoolResponse firstDayPool = pool(
                List.of(
                        candidate(1L, "관광지 1", "TOUR"),
                        candidate(2L, "관광지 2", "TOUR"),
                        candidate(3L, "관광지 3", "TOUR")
                ),
                List.of(
                        candidate(11L, "식당 1", "RESTAURANT"),
                        candidate(12L, "식당 2", "RESTAURANT")
                ),
                List.of(candidate(21L, "카페 1", "CAFE")),
                List.of(
                        candidate(100L, "숙소 1", "HOTEL"),
                        candidate(101L, "숙소 2", "HOTEL")
                )
        );
        when(placeRecommendationService.recommendCandidatePool(
                eq("ATLSP"),
                eq("서울"),
                eq("P"),
                eq("COUPLE"),
                anySet(),
                eq(2)
        )).thenReturn(firstDayPool);

        CourseDraftResponse draft = new CourseDraftService(
                travelSurveyRepository,
                surveyResultRepository,
                placeRecommendationService
        ).createDraft(1L);

        assertEquals(LocalTime.of(11, 0), draft.getDailyStartTime());
        assertEquals(List.of("종로구", "중구"), draft.getPreferredRegions());
        assertEquals(2, draft.getDailyPlans().size());
        assertEquals(2, draft.getHotelCandidates().size());
        assertEquals(6, draft.getDailyPlans().get(0).getTargetPlaceCount());
        assertEquals(
                Map.of(
                        "TOUR", 3,
                        "RESTAURANT", 2,
                        "CAFE", 1,
                        "HOTEL", 0
                ),
                draft.getDailyPlans().get(0).getCategoryTargets()
        );
        assertEquals(6, draft.getDailyPlans().get(0).getPlaceCandidates().size());
        assertEquals(LocalDate.of(2026, 7, 21),
                draft.getDailyPlans().get(1).getVisitDate());
        assertTrue(draft.getHotelCandidates().stream()
                .allMatch(candidate -> "HOTEL".equals(candidate.getCategory())));

        Set<Long> firstDayIds = draft.getDailyPlans().get(0)
                .getPlaceCandidates().stream()
                .map(PlaceRecommendationResponse::getPlaceId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> secondDayIds = draft.getDailyPlans().get(1)
                .getPlaceCandidates().stream()
                .map(PlaceRecommendationResponse::getPlaceId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(firstDayIds, secondDayIds);

        verify(placeRecommendationService, times(1)).recommendCandidatePool(
                eq("ATLSP"),
                eq("서울"),
                eq("P"),
                eq("COUPLE"),
                eq(Set.of()),
                eq(2)
        );
    }

    @Test
    void relaxedScheduleStartsAtOnePm() {
        TravelSurveyRepository travelSurveyRepository = mock(TravelSurveyRepository.class);
        SurveyResultRepository surveyResultRepository = mock(SurveyResultRepository.class);
        PlaceRecommendationService placeRecommendationService = mock(PlaceRecommendationService.class);
        TravelSurvey survey = mock(TravelSurvey.class);
        SurveyResult surveyResult = mock(SurveyResult.class);

        when(survey.getSurveyId()).thenReturn(2L);
        when(survey.getRegion()).thenReturn("서울");
        when(survey.getStartDate()).thenReturn(LocalDate.of(2026, 7, 20));
        when(survey.getEndDate()).thenReturn(LocalDate.of(2026, 7, 20));
        when(survey.getCompanionType()).thenReturn("FRIEND");
        when(survey.getTransportType()).thenReturn("WALKING");
        when(surveyResult.getResultId()).thenReturn(102L);
        when(surveyResult.getTravelCode()).thenReturn("ATLSR");
        when(travelSurveyRepository.findById(2L)).thenReturn(Optional.of(survey));
        when(surveyResultRepository.findBySurveyId(2L)).thenReturn(Optional.of(surveyResult));
        when(placeRecommendationService.recommendCandidatePool(
                eq("ATLSR"), eq("서울"), eq("R"), eq("FRIEND"), anySet(),
                eq(1)
        )).thenReturn(pool(
                List.of(candidate(1L, "관광지 1", "TOUR"), candidate(2L, "관광지 2", "TOUR")),
                List.of(candidate(11L, "식당 1", "RESTAURANT")),
                List.of(candidate(21L, "카페 1", "CAFE")),
                List.of()
        ));

        CourseDraftResponse draft = new CourseDraftService(
                travelSurveyRepository, surveyResultRepository, placeRecommendationService
        ).createDraft(2L);

        assertEquals(LocalTime.of(13, 0), draft.getDailyStartTime());
        assertEquals(4, draft.getDailyTargetPlaceCount());
        assertEquals(
                Map.of(
                        "TOUR", 2,
                        "RESTAURANT", 1,
                        "CAFE", 1,
                        "HOTEL", 0
                ),
                draft.getDailyCategoryTargets()
        );
    }

    private PlaceCandidatePoolResponse pool(
            List<PlaceRecommendationResponse> tours,
            List<PlaceRecommendationResponse> restaurants,
            List<PlaceRecommendationResponse> cafes,
            List<PlaceRecommendationResponse> hotels
    ) {
        Map<String, List<PlaceRecommendationResponse>> candidates =
                new LinkedHashMap<>();
        candidates.put("TOUR", tours);
        candidates.put("RESTAURANT", restaurants);
        candidates.put("CAFE", cafes);

        Map<String, List<PlaceRecommendationResponse>> fallback =
                new LinkedHashMap<>();
        fallback.put("TOUR", List.of());
        fallback.put("RESTAURANT", List.of());
        fallback.put("CAFE", List.of());

        return new PlaceCandidatePoolResponse(
                "ATLSP",
                "P",
                "COUPLE",
                List.of("종로구", "중구"),
                48,
                54,
                candidates,
                fallback,
                hotels,
                List.of()
        );
    }

    private PlaceRecommendationResponse candidate(
            Long id,
            String name,
            String category
    ) {
        Place place = new Place();
        place.setPlaceId(id);
        place.setName(name);
        place.setCategory(category);
        place.setRegion("서울");
        place.setLatitude(37.5 + id / 10_000.0);
        place.setLongitude(127.0 + id / 10_000.0);
        place.setImageUrl("https://example.com/" + id + ".jpg");
        return new PlaceRecommendationResponse(place, 90.0, List.of());
    }
}
