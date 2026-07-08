package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "TRAVEL_SURVEY")
@Getter
@Setter
@NoArgsConstructor
public class TravelSurvey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SURVEY_ID")
    private Long surveyId;

    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "REGION", nullable = false)
    private String region;

    @Column(name = "START_DATE", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "END_DATE", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "PEOPLE_COUNT", nullable = false)
    private Integer peopleCount;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}