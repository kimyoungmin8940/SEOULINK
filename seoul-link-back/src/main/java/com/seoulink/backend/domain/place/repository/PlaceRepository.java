package com.seoulink.backend.domain.place.repository;

import com.seoulink.backend.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    @Query("select p from Place p where p.isActive = 'Y' and (lower(p.name) like lower(concat('%', :keyword, '%')) or lower(p.address) like lower(concat('%', :keyword, '%'))) order by p.rating desc")
    List<Place> searchActive(@Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);
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
