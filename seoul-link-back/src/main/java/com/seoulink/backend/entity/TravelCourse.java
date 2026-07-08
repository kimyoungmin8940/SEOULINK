package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "TRAVEL_COURSES")
@Getter
@Setter
@NoArgsConstructor
public class TravelCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COURSE_ID")
    private Long courseId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "RESULT_ID")
    private Long resultId;

    @Column(name = "PAYMENT_ID")
    private Long paymentId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "TRAVEL_CODE", length = 5)
    private String travelCode;

    @Column(name = "COURSE_TYPE", nullable = false, length = 20)
    private String courseType = "CUSTOM";

    @Column(name = "REGION", length = 100)
    private String region;

    @Column(name = "IS_PUBLIC", nullable = false, length = 1)
    private String isPublic = "N";

    @Column(name = "VIEW_COUNT", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (courseType == null) courseType = "CUSTOM";
        if (isPublic == null) isPublic = "N";
        if (viewCount == null) viewCount = 0;
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void increaseViewCount() {
        if (viewCount == null) viewCount = 0;
        viewCount++;
    }

    public void updateBasicInfo(String title, String description, String region, String isPublic) {
        this.title = title;
        this.description = description;
        this.region = region;
        this.isPublic = isPublic;
    }

    public void makePublic() {
        this.isPublic = "Y";
    }

    public void makePrivate() {
        this.isPublic = "N";
    }
}