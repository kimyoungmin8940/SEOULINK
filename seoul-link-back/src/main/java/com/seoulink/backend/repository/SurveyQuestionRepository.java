package com.seoulink.backend.repository;

import com.seoulink.backend.entity.SurveyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {
    List<SurveyQuestion> findAllByOrderByQuestionIdAsc();
}