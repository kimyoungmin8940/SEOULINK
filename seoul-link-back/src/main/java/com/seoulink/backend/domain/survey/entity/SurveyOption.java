package com.seoulink.backend.domain.survey.entity;

/**
 * ERD의 {@code SURVEY_OPTION} 테이블과 매핑될 설문 선택지 엔티티이다.
 *
 * <p>각 질문에 속하는 선택지 문구와 여행 유형별 점수 정보를 저장한다.</p>
 */

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설문 선택지 정보를 저장하는 SURVEY_OPTION 테이블의 엔티티이다.
 */
@Entity
@Table(name = "SURVEY_OPTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "OPTION_ID")
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "QUESTION_ID",
            nullable = false
    )
    private SurveyQuestion question;

    @Column(
            name = "OPTION_TEXT",
            nullable = false,
            length = 300
    )
    private String optionText;

    @Column(
            name = "SCORE_CODE",
            nullable = false,
            length = 20
    )
    private String scoreCode;

    @Column(
            name = "SCORE_VALUE",
            nullable = false
    )
    private Integer scoreValue;

    @Column(
            name = "IMAGE_URL",
            length = 500
    )
    private String imageUrl;
}
