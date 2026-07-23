package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseDraftResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationListResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.service.PlaceRecommendationService;
import com.seoulink.backend.domain.survey.entity.SurveyResult;
import com.seoulink.backend.domain.survey.entity.TravelSurvey;
import com.seoulink.backend.domain.survey.repository.SurveyResultRepository;
import com.seoulink.backend.domain.survey.repository.TravelSurveyRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseDraftServiceTest {

    @Test
    void forwardsCompanionTypeAndSeparatesHotelCandidates() {
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
        when(surveyResult.getTravelCode()).thenReturn("ATLSR");
        when(travelSurveyRepository.findById(1L))
                .thenReturn(Optional.of(survey));
        when(surveyResultRepository.findBySurveyId(1L))
                .thenReturn(Optional.of(surveyResult));

        List<PlaceRecommendationResponse> recommendations = List.of(
                response(place(1L, "관광지", "TOUR")),
                response(place(2L, "식당", "RESTAURANT")),
                response(place(3L, "카페", "CAFE")),
                response(place(100L, "숙소 1", "HOTEL")),
                response(place(101L, "숙소 2", "HOTEL"))
        );
        when(placeRecommendationService.recommend(
                "ATLSR",
                "서울",
                null,
                8,
                3,
                "COUPLE"
        )).thenReturn(new PlaceRecommendationListResponse(
                "ATLSR",
                recommendations
        ));

        CourseDraftResponse draft = new CourseDraftService(
                travelSurveyRepository,
                surveyResultRepository,
                placeRecommendationService
        ).createDraft(1L);

        assertEquals(2, draft.getHotelCandidates().size());
        assertTrue(draft.getHotelCandidates().stream()
                .allMatch(candidate -> "HOTEL".equals(candidate.getCategory())));
        assertTrue(draft.getDailyPlans().stream()
                .flatMap(day -> day.getPlaceCandidates().stream())
                .noneMatch(candidate -> "HOTEL".equals(candidate.getCategory())));
        verify(placeRecommendationService).recommend(
                "ATLSR",
                "서울",
                null,
                8,
                3,
                "COUPLE"
        );
    }

    private Place place(Long id, String name, String category) {
        Place place = new Place();
        place.setPlaceId(id);
        place.setName(name);
        place.setCategory(category);
        place.setRegion("서울");
        place.setAddress("서울 " + name);
        place.setLatitude(37.5 + id / 10_000.0);
        place.setLongitude(127.0 + id / 10_000.0);
        place.setImageUrl("https://example.com/" + id + ".jpg");
        if ("HOTEL".equals(category)) {
            place.setThemeHotelStayYn("Y");
        }
        return place;
    }

    private PlaceRecommendationResponse response(Place place) {
        return new PlaceRecommendationResponse(place, 90.0, List.of());
    }
}
