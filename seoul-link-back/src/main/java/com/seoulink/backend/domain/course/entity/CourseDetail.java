package com.seoulink.backend.domain.course.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "COURSE_DETAILS")
@Getter
@Setter
@NoArgsConstructor
public class CourseDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DETAIL_ID")
    private Long detailId;

    @Column(name = "COURSE_ID", nullable = false)
    private Long courseId;

    @Column(name = "PLACE_ID", nullable = false)
    private Long placeId;

    @Column(name = "DAY_NO", nullable = false)
    private Integer dayNo = 1;

    @Column(name = "PLACE_ORDER", nullable = false)
    private Integer placeOrder;

    @Column(name = "MEMO")
    private String memo;

    @Column(name = "VISIT_TIME")
    private String visitTime;

    @Column(name = "STAY_MINUTES")
    private Integer stayMinutes;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}