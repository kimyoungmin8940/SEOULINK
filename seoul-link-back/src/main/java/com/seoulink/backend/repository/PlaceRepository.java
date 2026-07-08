package com.seoulink.backend.repository;

import com.seoulink.backend.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {
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

}
