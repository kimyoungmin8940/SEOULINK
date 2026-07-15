package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 추천 상세페이지에서 사용하는 저장 코스 조회 응답이다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDetailResponse {

    private Long courseId;
    private String title;
    private String description;
    private String coverImageUrl;
    private String travelCode;
    private String courseType;
    private String region;
    private Boolean publicCourse;
    private Long viewCount;
    private Integer placeCount;
    private Integer dayCount;
    private Double totalDistanceKm;
    private Double totalTravelTimeMinutes;
    private Integer totalVisitTimeMinutes;
    private Double totalCourseTimeMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<CoursePlaceResponse> places = new ArrayList<>();
}
