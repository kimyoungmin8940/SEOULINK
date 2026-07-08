package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "SURVEY_OPTION")
@Getter
@Setter
@NoArgsConstructor
public class SurveyOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "OPTION_ID")
    private Long optionId;

    @Column(name = "QUESTION_ID", nullable = false)
    private Long questionId;

    @Column(name = "OPTION_TEXT", nullable = false)
    private String optionText;

    @Column(name = "SCORE_CODE", nullable = false)
    private String scoreCode;

    @Column(name = "SCORE_VALUE", nullable = false)
    private Integer scoreValue;
}