package com.seoulink.backend.domain.place.service;

import com.seoulink.backend.domain.place.dto.request.PlaceCreateRequest;
import com.seoulink.backend.domain.place.dto.response.PlaceResponse;
import com.seoulink.backend.domain.place.entity.Place;
import com.seoulink.backend.domain.place.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceTaggingService placeTaggingService;

    public PlaceService(PlaceRepository placeRepository, PlaceTaggingService placeTaggingService) {
        this.placeRepository = placeRepository;
        this.placeTaggingService = placeTaggingService;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> getPlaces(String region, String category) {
        if (category == null || category.isBlank()) {
            return placeRepository.findByRegionContainingAndIsActive(region, "Y")
                    .stream()
                    .map(PlaceResponse::new)
                    .toList();
        }

        return placeRepository.findByRegionContainingAndCategoryAndIsActive(region, category, "Y")
                .stream()
                .map(PlaceResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
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
                    )
                    .stream()
                    .map(PlaceResponse::new)
                    .toList();
        }

        return placeRepository.findByLatitudeBetweenAndLongitudeBetweenAndCategoryAndIsActive(
                        minLat,
                        maxLat,
                        minLng,
                        maxLng,
                        category,
                        "Y"
                )
                .stream()
                .map(PlaceResponse::new)
                .toList();
    }

    public PlaceResponse createPlace(PlaceCreateRequest request) {
        // 지도 API를 다시 수집해도 같은 제공자·장소 ID의 행이 중복 생성되지 않게 갱신한다.
        Place place = placeRepository.findByApiProviderAndApiPlaceId(
                        request.getApiProvider(),
                        request.getApiPlaceId()
                )
                .orElseGet(Place::new);

        applyRequest(place, request);
        place.setIsActive("Y");

        // API 기본정보가 바뀔 수 있으므로 신규·기존 장소 모두 태그를 다시 계산한다.
        placeTaggingService.applyTags(place);
        applyRecommendationOverrides(place, request);

        return new PlaceResponse(placeRepository.save(place));
    }

    public PlaceResponse updatePlace(Long placeId, PlaceCreateRequest request) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new IllegalArgumentException("장소를 찾을 수 없습니다."));

        applyRequest(place, request);
        placeTaggingService.applyTags(place);
        applyRecommendationOverrides(place, request);

        return new PlaceResponse(place);
    }

    public void deactivatePlace(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new IllegalArgumentException("장소를 찾을 수 없습니다."));

        place.setIsActive("N");
    }

    private void applyRequest(Place place, PlaceCreateRequest request) {
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
    }

    /**
     * 자동 태깅 결과를 기본값으로 사용하되, 관리자가 Y/N을 명시한 필드만 마지막에 보정한다.
     * null은 "자동 분류 사용"을 뜻하므로 기존 자동 태그를 건드리지 않는다.
     */
    private void applyRecommendationOverrides(Place place, PlaceCreateRequest request) {
        if (request.getTagHistory() != null) place.setTagHistory(request.getTagHistory());
        if (request.getTagModern() != null) place.setTagModern(request.getTagModern());
        if (request.getTagBudget() != null) place.setTagBudget(request.getTagBudget());
        if (request.getTagLuxury() != null) place.setTagLuxury(request.getTagLuxury());
        if (request.getTagStable() != null) place.setTagStable(request.getTagStable());
        if (request.getTagDopamine() != null) place.setTagDopamine(request.getTagDopamine());
        if (request.getTagRelax() != null) place.setTagRelax(request.getTagRelax());
        if (request.getTagPacked() != null) place.setTagPacked(request.getTagPacked());

        if (request.getThemePalaceCultureYn() != null) {
            place.setThemePalaceCultureYn(request.getThemePalaceCultureYn());
        }
        if (request.getThemeNatureHangangYn() != null) {
            place.setThemeNatureHangangYn(request.getThemeNatureHangangYn());
        }
        if (request.getThemeDateYn() != null) place.setThemeDateYn(request.getThemeDateYn());
        if (request.getThemeFoodTourYn() != null) place.setThemeFoodTourYn(request.getThemeFoodTourYn());
        if (request.getThemeCafeTourYn() != null) place.setThemeCafeTourYn(request.getThemeCafeTourYn());
        if (request.getThemeShoppingHotplaceYn() != null) {
            place.setThemeShoppingHotplaceYn(request.getThemeShoppingHotplaceYn());
        }
        if (request.getThemeNightViewYn() != null) place.setThemeNightViewYn(request.getThemeNightViewYn());
        if (request.getThemeHotelStayYn() != null) place.setThemeHotelStayYn(request.getThemeHotelStayYn());
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> getPlacesByNames(
            List<String> names
    ) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }

        return placeRepository
                .findByNameInAndIsActive(
                        names,
                        "Y"
                )
                .stream()
                .map(PlaceResponse::new)
                .toList();
    }
}
