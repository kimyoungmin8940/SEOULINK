package com.seoulink.backend.dto.response;

import com.seoulink.backend.entity.TravelCourse;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class CourseResponse {

    private Long courseId;
    private Long memberId;
    private Long resultId;
    private Long paymentId;

    private String title;
    private String description;
    private String travelCode;
    private String courseType;
    private String region;
    private String isPublic;

    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<CourseDetailResponse> details;

    public CourseResponse(TravelCourse course, List<CourseDetailResponse> details) {
        this.courseId = course.getCourseId();
        this.memberId = course.getMemberId();
        this.resultId = course.getResultId();
        this.paymentId = course.getPaymentId();

        this.title = course.getTitle();
        this.description = course.getDescription();
        this.travelCode = course.getTravelCode();
        this.courseType = course.getCourseType();
        this.region = course.getRegion();
        this.isPublic = course.getIsPublic();

        this.viewCount = course.getViewCount();
        this.createdAt = course.getCreatedAt();
        this.updatedAt = course.getUpdatedAt();

        this.details = details;
    }
}