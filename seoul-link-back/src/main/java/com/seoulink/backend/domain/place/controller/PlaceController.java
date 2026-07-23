package com.seoulink.backend.domain.place.controller;

import com.seoulink.backend.domain.place.dto.request.PlaceCreateRequest;
import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationListResponse;
import com.seoulink.backend.domain.place.dto.response.PlaceResponse;
import com.seoulink.backend.domain.place.service.PlaceRecommendationService;
import com.seoulink.backend.domain.place.service.PlaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService placeService;
    private final PlaceRecommendationService placeRecommendationService;

    public PlaceController(
            PlaceService placeService,
            PlaceRecommendationService placeRecommendationService
    ) {
        this.placeService = placeService;
        this.placeRecommendationService = placeRecommendationService;
    }

    @GetMapping
    public List<PlaceResponse> getPlaces(
            @RequestParam String region,
            @RequestParam(required = false) String category
    ) {
        return placeService.getPlaces(region, category);
    }

    @GetMapping("/bounds")
    public List<PlaceResponse> getPlacesInBounds(
            @RequestParam Double minLat,
            @RequestParam Double maxLat,
            @RequestParam Double minLng,
            @RequestParam Double maxLng,
            @RequestParam(required = false) String category
    ) {
        return placeService.getPlacesInBounds(minLat, maxLat, minLng, maxLng, category);
    }

    @GetMapping("/recommend")
    public PlaceRecommendationListResponse recommendPlaces(
            @RequestParam String travelCode,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String companionType,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer limitPerCategory,
            @RequestParam(required = false) Integer alternativeLimit
    ) {
        return placeRecommendationService.recommend(
                travelCode,
                region,
                limit,
                limitPerCategory,
                alternativeLimit,
                companionType
        );
    }

    @PostMapping
    public PlaceResponse createPlace(@Valid @RequestBody PlaceCreateRequest request) {
        return placeService.createPlace(request);
    }

    @PutMapping("/{placeId}")
    public PlaceResponse updatePlace(
            @PathVariable Long placeId,
            @Valid @RequestBody PlaceCreateRequest request
    ) {
        return placeService.updatePlace(placeId, request);
    }

    @DeleteMapping("/{placeId}")
    public void deactivatePlace(@PathVariable Long placeId) {
        placeService.deactivatePlace(placeId);
    }
}
