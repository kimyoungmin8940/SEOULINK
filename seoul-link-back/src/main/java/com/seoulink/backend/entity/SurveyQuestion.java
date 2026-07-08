package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "SURVEY_QUESTION")
@Getter
@Setter
@NoArgsConstructor
public class SurveyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QUESTION_ID")
    private Long questionId;

    @Column(name = "QUESTION_TEXT", nullable = false)
    private String questionText;

    @Column(name = "CATEGORY", nullable = false)
    private String category;
}