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

import java.math.BigDecimal;
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

    //날씨 상태
    @Column(
            name = "WEATHER_STATUS",
            length = 50
    )
    private String weatherStatus;

    //여행지의 기온
    @Column(
            name = "TEMPERATURE",
            precision = 4,
            scale = 1
    )
    private BigDecimal temperature;

    //강수 확률
    @Column(name = "RAIN_PROBABILITY")
    private Integer rainProbability;

    @Column(
            name = "CREATED_AT",
            nullable = false
    )
    private LocalDateTime createdAt;

    //날씨 정보 없이 설문 결과를 생성
    public static SurveyResult create(
            Long surveyId,
            String travelCode
    ) {
        validateSurveyId(surveyId);
        validateTravelCode(travelCode);

        SurveyResult result = new SurveyResult();

        result.surveyId = surveyId;
        result.travelCode = travelCode;
        result.createdAt = LocalDateTime.now();

        return result;
    }

    //날씨 정보를 포함하여 설문 결과를 생성
    public static SurveyResult createWithWeather(
            Long surveyId,
            String travelCode,
            String weatherStatus,
            BigDecimal temperature,
            Integer rainProbability
    ) {
        validateSurveyId(surveyId);
        validateTravelCode(travelCode);
        validateRainProbability(rainProbability);

        SurveyResult result = new SurveyResult();

        result.surveyId = surveyId;
        result.travelCode = travelCode;
        result.weatherStatus = weatherStatus;
        result.temperature = temperature;
        result.rainProbability = rainProbability;
        result.createdAt = LocalDateTime.now();

        return result;
    }

    //날씨 정보를 새로 입력하거나 갱신

    public void updateWeather(
            String weatherStatus,
            BigDecimal temperature,
            Integer rainProbability
    ) {
        validateRainProbability(rainProbability);

        this.weatherStatus = weatherStatus;
        this.temperature = temperature;
        this.rainProbability = rainProbability;
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

    private static void validateRainProbability(
            Integer rainProbability
    ) {
        if (rainProbability != null &&
                (rainProbability < 0 ||
                        rainProbability > 100)) {
            throw new IllegalArgumentException(
                    "강수 확률은 0부터 100 사이여야 합니다."
            );
        }
    }
}
