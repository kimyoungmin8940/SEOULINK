package com.seoulink.backend.domain.survey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
/**
 * ERD의 {@code SURVEY_ANSWER} 테이블과 매핑될 사용자 설문 답변 엔티티이다.
 *
 * <p>어떤 설문에서 어떤 질문의 어떤 선택지를 골랐는지 기록한다.</p>
 */
@Entity
@Table(
        name = "SURVEY_ANSWER",
        uniqueConstraints = {
                @UniqueConstraint(  //한 번의 설문에서 같은 질문에 대한 답변이 두 개 저장되는 것을 막음
                        name = "UK_ANSWER_SURVEY_QUESTION",
                        columnNames = {
                                "SURVEY_ID",
                                "QUESTION_ID"
                        }
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ANSWER_ID")
    private Long answerId;

    //답변이 속한 설문 실행 번호
    @Column(
            name = "SURVEY_ID",
            nullable = false
    )
    private Long surveyId;

    //사용자가 답변한 질문 번호
    @Column(
            name = "QUESTION_ID",
            nullable = false
    )
    private Long questionId;

    //사용자가 선택한 선택지 번호
    @Column(
            name = "OPTION_ID",
            nullable = false
    )
    private Long optionId;

    //사용자가 선택한 답변 생성
    public static SurveyAnswer create(
            Long surveyId,
            Long questionId,
            Long optionId
    ) {
        validateId(surveyId, "설문 번호");
        validateId(questionId, "질문 번호");
        validateId(optionId, "선택지 번호");

        SurveyAnswer answer = new SurveyAnswer();

        answer.surveyId = surveyId;
        answer.questionId = questionId;
        answer.optionId = optionId;

        return answer;
    }

    private static void validateId(
            Long id,
            String fieldName
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + "가 올바르지 않습니다."
            );
        }
    }
}
