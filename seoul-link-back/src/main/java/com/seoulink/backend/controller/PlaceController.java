package com.seoulink.backend.controller;

import com.seoulink.backend.dto.response.PlaceResponse;
import com.seoulink.backend.service.PlaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.seoulink.backend.dto.request.PlaceCreateRequest;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
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
