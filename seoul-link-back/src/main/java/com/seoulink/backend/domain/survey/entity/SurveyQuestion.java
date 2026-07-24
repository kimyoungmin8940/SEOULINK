package com.seoulink.backend.domain.survey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * ERD의 {@code SURVEY_QUESTION} 테이블과 매핑될 설문 질문 엔티티이다.
 *
 * <p>질문 문구, 질문 카테고리, 노출 순서 등 설문 질문의 기준 정보를 관리한다.</p>
 */

@Entity
@Table(name = "SURVEY_QUESTION")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_ID")
    private Long questionId;

    @Column(
            name = "QUESTION_TEXT",
            nullable = false,
            length = 500
    )
    private String questionText;

    @Column(
            name = "CATEGORY",
            nullable = false,
            length = 30
    )
    private String category;

    @Column(
            name = "DISPLAY_ORDER",
            nullable = false
    )
    private Integer displayOrder;

    @OneToMany(mappedBy = "question")
    @OrderBy("optionId ASC")
    private List<SurveyOption> options = new ArrayList<>();
}