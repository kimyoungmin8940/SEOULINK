package com.seoulink.backend.domain.mypage.dto.response;

import com.seoulink.backend.domain.course.entity.TravelCourse;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MyCourseResponse {
    private Long courseId;
    private String title;
    private String description;
    private String travelCode;
    private String courseType;
    private String region;
    private Integer viewCount;
    private LocalDateTime createdAt;

    public MyCourseResponse(TravelCourse course) {
        this.courseId = course.getCourseId();
        this.title = course.getTitle();
        this.description = course.getDescription();
        this.travelCode = course.getTravelCode();
        this.courseType = course.getCourseType();
        this.region = course.getRegion();
        this.viewCount = course.getViewCount();
        this.createdAt = course.getCreatedAt();
    }
}