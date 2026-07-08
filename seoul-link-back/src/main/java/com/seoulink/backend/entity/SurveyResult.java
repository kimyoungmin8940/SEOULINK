package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "SURVEY_RESULT")
@Getter
@Setter
@NoArgsConstructor
public class SurveyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RESULT_ID")
    private Long resultId;

    @Column(name = "SURVEY_ID", nullable = false)
    private Long surveyId;

    @Column(name = "TRAVEL_CODE", nullable = false)
    private String travelCode;

    @Column(name = "WEATHER_STATUS")
    private String weatherStatus;

    @Column(name = "TEMPERATURE")
    private Double temperature;

    @Column(name = "RAIN_PROBABILITY")
    private Integer rainProbability;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}