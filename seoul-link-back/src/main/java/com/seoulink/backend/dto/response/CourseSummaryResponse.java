package com.seoulink.backend.dto.response;

import com.seoulink.backend.entity.TravelCourse;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CourseSummaryResponse {

    private Long courseId;
    private Long memberId;
    private String title;
    private String description;
    private String travelCode;
    private String courseType;
    private String region;
    private String isPublic;
    private Integer viewCount;
    private Integer placeCount;
    private LocalDateTime createdAt;

    public CourseSummaryResponse(TravelCourse course, Integer placeCount) {
        this.courseId = course.getCourseId();
        this.memberId = course.getMemberId();
        this.title = course.getTitle();
        this.description = course.getDescription();
        this.travelCode = course.getTravelCode();
        this.courseType = course.getCourseType();
        this.region = course.getRegion();
        this.isPublic = course.getIsPublic();
        this.viewCount = course.getViewCount();
        this.placeCount = placeCount;
        this.createdAt = course.getCreatedAt();
    }
}