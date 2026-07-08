package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "SURVEY_ANSWER")
@Getter
@Setter
@NoArgsConstructor
public class SurveyAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ANSWER_ID")
    private Long answerId;

    @Column(name = "SURVEY_ID", nullable = false)
    private Long surveyId;

    @Column(name = "QUESTION_ID", nullable = false)
    private Long questionId;

    @Column(name = "OPTION_ID", nullable = false)
    private Long optionId;
}