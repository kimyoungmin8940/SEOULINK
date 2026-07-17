package com.seoulink.backend.domain.survey.repository;

import com.seoulink.backend.domain.survey.entity.SurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 설문 답변({@code SurveyAnswer}) 저장·조회 기능을 담당할 Repository이다.
 *
 * <p>Spring Data JPA 구현 시 이 인터페이스가 해당 엔티티의
 * {@code JpaRepository<엔티티, 기본키타입>}를 상속하도록 수정한다.</p>
 */
public interface SurveyAnswerRepository
        extends JpaRepository<SurveyAnswer, Long> {

    /**
     * 하나의 설문에 저장된 전체 답변을 조회한다.
     */
    List<SurveyAnswer> findBySurveyIdOrderByQuestionIdAsc(
            Long surveyId
    );

    /**
     * 특정 설문의 특정 질문에 대한 답변을 조회한다.
     */
    Optional<SurveyAnswer> findBySurveyIdAndQuestionId(
            Long surveyId,
            Long questionId
    );

    /**
     * 특정 설문에 저장된 답변 개수를 조회한다.
     */
    long countBySurveyId(Long surveyId);

    /**
     * 특정 설문에 해당 질문의 답변이 이미 있는지 확인한다.
     */
    boolean existsBySurveyIdAndQuestionId(
            Long surveyId,
            Long questionId
    );
}
