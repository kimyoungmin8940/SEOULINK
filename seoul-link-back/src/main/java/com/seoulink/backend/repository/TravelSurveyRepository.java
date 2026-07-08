package com.seoulink.backend.repository;

import com.seoulink.backend.entity.TravelSurvey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelSurveyRepository extends JpaRepository<TravelSurvey, Long> {
}