package com.seoulink.backend.repository;

import com.seoulink.backend.entity.SurveyResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SurveyResultRepository extends JpaRepository<SurveyResult, Long> {

    @Query("""
        select sr
        from SurveyResult sr
        join TravelSurvey ts on sr.surveyId = ts.surveyId
        where ts.memberId = :memberId
        order by sr.createdAt desc
    """)
    List<SurveyResult> findResultsByMemberId(Long memberId);
}