package com.seoulink.backend.domain.survey.repository;

import com.seoulink.backend.domain.survey.entity.SurveyOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
/**
 * 설문 선택지({@code SurveyOption}) 조회 기능을 담당할 Repository이다.
 * 질문 ID별 선택지 목록 조회 메서드를 정의한다.
 *
 * <p>Spring Data JPA 구현 시 이 인터페이스가 해당 엔티티의
 * {@code JpaRepository<엔티티, 기본키타입>}를 상속하도록 수정한다.</p>
 */
public interface SurveyOptionRepository extends JpaRepository<SurveyOption, Long> {

    //특정 질문에 속한 선택지를 OPTION_ID 오름차순으로 조회
    List<SurveyOption> findByQuestionQuestionIdOrderByOptionIdAsc(
            Long questionId
    );

    //선택지가 해당 질문에 실제로 속하는지 함께 확인
    Optional<SurveyOption> findByOptionIdAndQuestionQuestionId(
            Long optionId,
            Long questionId
    );
}
