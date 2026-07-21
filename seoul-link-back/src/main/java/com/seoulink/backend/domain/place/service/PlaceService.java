package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.dto.response.PlaceResponse;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import com.seoulink.backend.domain.place.dto.request.PlaceCreateRequest;
import com.seoulink.backend.domain.place.entity.Place;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PlaceService {

    private final PlaceRepository placeRepository;

    public PlaceService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    public List<PlaceResponse> getPlaces(String region, String category) {
        if (category == null || category.isBlank()) {
            return placeRepository.findByRegionContainingAndIsActive(region, "Y")
                    .stream().map(PlaceResponse::new).toList();
        }

        return placeRepository.findByRegionContainingAndCategoryAndIsActive(region, category, "Y")
                .stream().map(PlaceResponse::new).toList();
    }

    public List<PlaceResponse> searchPlaces(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return placeRepository.searchActive(keyword.trim(), org.springframework.data.domain.PageRequest.of(0, 8))
                .stream().map(PlaceResponse::new).toList();
    }

    public List<PlaceResponse> getPlacesInBounds(
            Double minLat,
            Double maxLat,
            Double minLng,
            Double maxLng,
            String category
    ) {
        if (category == null || category.isBlank()) {
            return placeRepository.findByLatitudeBetweenAndLongitudeBetweenAndIsActive(
                    minLat,
                    maxLat,
                    minLng,
                    maxLng,
                    "Y"
            ).stream().map(PlaceResponse::new).toList();
        }

        return placeRepository.findByLatitudeBetweenAndLongitudeBetweenAndCategoryAndIsActive(
                minLat,
                maxLat,
                minLng,
                maxLng,
                category,
                "Y"
        ).stream().map(PlaceResponse::new).toList();
    }

    public PlaceResponse createPlace(PlaceCreateRequest request) {
        Place place = new Place();
        place.setApiProvider(request.getApiProvider());
        place.setApiPlaceId(request.getApiPlaceId());
        place.setContentId(request.getContentId());

        place.setName(request.getName());
        place.setCategory(request.getCategory());
        place.setApiCategory(request.getApiCategory());

        place.setRegion(request.getRegion());
        place.setAddress(request.getAddress());
        place.setRoadAddress(request.getRoadAddress());

        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());

        place.setPhone(request.getPhone());
        place.setPlaceUrl(request.getPlaceUrl());

        place.setRating(request.getRating());
        place.setReviewCount(request.getReviewCount());

        place.setDescription(request.getDescription());
        place.setImageUrl(request.getImageUrl());

        place.setTagHistory(request.getTagHistory());
        place.setTagModern(request.getTagModern());
        place.setTagBudget(request.getTagBudget());
        place.setTagLuxury(request.getTagLuxury());
        place.setTagStable(request.getTagStable());
        place.setTagDopamine(request.getTagDopamine());
        place.setTagRelax(request.getTagRelax());
        place.setTagPacked(request.getTagPacked());

        place.setIsActive("Y");

        return new PlaceResponse(placeRepository.save(place));
    }

    public PlaceResponse updatePlace(Long placeId, PlaceCreateRequest request) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new IllegalArgumentException("장소를 찾을 수 없습니다."));

        place.setName(request.getName());
        place.setCategory(request.getCategory());
        place.setApiCategory(request.getApiCategory());
        place.setRegion(request.getRegion());
        place.setAddress(request.getAddress());
        place.setRoadAddress(request.getRoadAddress());
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        place.setPhone(request.getPhone());
        place.setPlaceUrl(request.getPlaceUrl());
        place.setRating(request.getRating());
        place.setReviewCount(request.getReviewCount());
        place.setDescription(request.getDescription());
        place.setImageUrl(request.getImageUrl());
        place.setTagHistory(request.getTagHistory());
        place.setTagModern(request.getTagModern());
        place.setTagBudget(request.getTagBudget());
        place.setTagLuxury(request.getTagLuxury());
        place.setTagStable(request.getTagStable());
        place.setTagDopamine(request.getTagDopamine());
        place.setTagRelax(request.getTagRelax());
        place.setTagPacked(request.getTagPacked());

        return new PlaceResponse(place);
    }

    public void deactivatePlace(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new IllegalArgumentException("장소를 찾을 수 없습니다."));

        place.setIsActive("N");
    }
}
