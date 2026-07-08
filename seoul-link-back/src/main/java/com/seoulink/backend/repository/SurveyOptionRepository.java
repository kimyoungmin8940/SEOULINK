package com.seoulink.backend.repository;

import com.seoulink.backend.entity.SurveyOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SurveyOptionRepository extends JpaRepository<SurveyOption, Long> {
    List<SurveyOption> findByQuestionIdOrderByOptionIdAsc(Long questionId);
}
