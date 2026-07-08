package com.seoulink.backend.repository;

import com.seoulink.backend.entity.TravelTypePlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelTypePlaceRepository extends JpaRepository<TravelTypePlace, Long> {
    List<TravelTypePlace> findTop10ByTravelCodeOrderByWeightScoreDesc(String travelCode);
    List<TravelTypePlace> findTop30ByTravelCodeOrderByWeightScoreDesc(String travelCode);
}