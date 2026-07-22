package com.seoulink.backend.domain.survey.repository;

import com.seoulink.backend.domain.survey.entity.SurveyResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 설문 결과({@code SurveyResult}) 저장·조회 기능을 담당할 Repository이다.
 *
 * <p>Spring Data JPA 구현 시 이 인터페이스가 해당 엔티티의
 * {@code JpaRepository<엔티티, 기본키타입>}를 상속하도록 수정한다.</p>
 */
public interface SurveyResultRepository
        extends JpaRepository<SurveyResult, Long> {

    /**
     * 설문 실행 번호로 검사 결과를 조회한다.
     */
    Optional<SurveyResult> findBySurveyId(
            Long surveyId
    );

    /**
     * 해당 설문에 결과가 이미 생성됐는지 확인한다.
     */
    boolean existsBySurveyId(
            Long surveyId
    );

    /**
     * 특정 여행 유형 코드에 해당하는 결과를 조회한다.
     */
    Optional<SurveyResult> findFirstByTravelCodeOrderByCreatedAtDesc(
            String travelCode
    );
}
