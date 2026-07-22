package com.seoulink.backend.domain.survey.repository;

import com.seoulink.backend.domain.survey.entity.SurveyQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
/**
 * 설문 질문({@code SurveyQuestion}) 조회 기능을 담당할 Repository이다.
 * 질문 순서에 따른 정렬 조회가 필요할 수 있다.
 *
 * <p>Spring Data JPA 구현 시 이 인터페이스가 해당 엔티티의
 * {@code JpaRepository<엔티티, 기본키타입>}를 상속하도록 수정한다.</p>
 */

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {
    //질문과 연결된 선택지를 함께 조회하고, 질문 표시 순서대로 정렬
    @EntityGraph(attributePaths = "options")
    List<SurveyQuestion> findAllByOrderByDisplayOrderAsc();
}
