package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.dto.request.PlaceCreateRequest;
import com.seoulink.backend.domain.place.dto.response.PlaceResponse;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceServiceTest {

    @Test
    void repeatedApiPlaceUpdatesExistingRowAndRetagsIt() {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        PlaceTaggingService placeTaggingService = mock(PlaceTaggingService.class);
        PlaceService placeService = new PlaceService(placeRepository, placeTaggingService);
        Place existing = new Place();
        existing.setPlaceId(10L);

        PlaceCreateRequest request = request();
        when(placeRepository.findByApiProviderAndApiPlaceId("KAKAO", "12345"))
                .thenReturn(Optional.of(existing));
        when(placeRepository.save(existing)).thenReturn(existing);

        PlaceResponse response = placeService.createPlace(request);

        assertEquals(10L, response.getPlaceId());
        assertEquals(10L, existing.getPlaceId());
        assertEquals("서울숲", existing.getName());
        assertEquals("Y", existing.getIsActive());
        verify(placeTaggingService).applyTags(existing);
        verify(placeRepository).save(existing);
    }

    @Test
    void explicitRecommendationFlagsOverrideAutomaticTags() {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        PlaceService placeService = new PlaceService(placeRepository, new PlaceTaggingService());
        PlaceCreateRequest request = request();
        request.setThemeNatureHangangYn("N");
        request.setTagRelax("N");

        when(placeRepository.findByApiProviderAndApiPlaceId("KAKAO", "12345"))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any(Place.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlaceResponse response = placeService.createPlace(request);

        assertEquals("N", response.getThemeNatureHangangYn());
        assertEquals("N", response.getTagRelax());
    }

    private PlaceCreateRequest request() {
        PlaceCreateRequest request = new PlaceCreateRequest();
        request.setApiProvider("KAKAO");
        request.setApiPlaceId("12345");
        request.setName("서울숲");
        request.setCategory("TOUR");
        request.setApiCategory("관광명소");
        request.setRegion("성동구");
        request.setAddress("서울 성동구 뚝섬로 273");
        request.setLatitude(37.5444);
        request.setLongitude(127.0374);
        request.setRating(4.7);
        request.setReviewCount(120);
        return request;
    }
}
