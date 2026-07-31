package com.seoulink.backend.domain.place.repository;

import com.seoulink.backend.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByApiProviderAndApiPlaceId(String apiProvider, String apiPlaceId);

    List<Place> findByIsActive(String isActive);

    List<Place> findTop10ByRegionAndIsActiveOrderByRatingDesc(String region, String isActive);

    List<Place> findByPlaceIdInAndIsActive(List<Long> placeIds, String isActive);

    List<Place> findByRegionContainingAndIsActive(String region, String isActive);

    List<Place> findByRegionContainingAndCategoryAndIsActive(String region, String category, String isActive);

    List<Place> findByLatitudeBetweenAndLongitudeBetweenAndIsActive(
            Double minLatitude,
            Double maxLatitude,
            Double minLongitude,
            Double maxLongitude,
            String isActive
    );

    List<Place> findByLatitudeBetweenAndLongitudeBetweenAndCategoryAndIsActive(
            Double minLatitude,
            Double maxLatitude,
            Double minLongitude,
            Double maxLongitude,
            String category,
            String isActive
    );

    List<Place> findByNameInAndIsActive(
            List<String> names,
            String isActive
    );
}
