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

import java.time.LocalDateTime;

/**
 * ERD의 {@code SURVEY_RESULT} 테이블과 매핑될 설문 결과 엔티티이다.
 *
 * <p>설문별 최종 여행 유형, 점수, 순위, 추천 가능 상태 등을 저장한다.</p>
 */

@Entity
@Table(
        name = "SURVEY_RESULT",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_RESULT_SURVEY",
                        columnNames = "SURVEY_ID"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RESULT_ID")
    private Long resultId;

    //결과가 속한 설문 실행 번호
    //하나의 설문에는 하나의 결과만 저장 가능
    @Column(
            name = "SURVEY_ID",
            nullable = false,
            unique = true
    )
    private Long surveyId;

    //검사 결과로 계산된 5글자 여행 유형 코드
    @Column(
            name = "TRAVEL_CODE",
            nullable = false,
            length = 5,
            columnDefinition = "CHAR(5)"
    )
    private String travelCode;

    @Column(
            name = "CREATED_AT",
            nullable = false
    )
    private LocalDateTime createdAt;

    // 설문 실행 번호와 여행 유형 코드로 결과를 생성한다.
    public static SurveyResult create(
            Long surveyId,
            String travelCode
    ) {
        validateSurveyId(surveyId);
        validateTravelCode(travelCode);

        SurveyResult result = new SurveyResult();

        result.surveyId = surveyId;
        result.travelCode =
                travelCode.trim().toUpperCase();
        result.createdAt = LocalDateTime.now();

        return result;
    }

    private static void validateSurveyId(Long surveyId) {
        if (surveyId == null || surveyId <= 0) {
            throw new IllegalArgumentException(
                    "설문 번호가 올바르지 않습니다."
            );
        }
    }

    private static void validateTravelCode(
            String travelCode
    ) {
        if (travelCode == null ||
                travelCode.isBlank() ||
                travelCode.length() != 5) {
            throw new IllegalArgumentException(
                    "여행 유형 코드는 5글자여야 합니다."
            );
        }
    }
}
