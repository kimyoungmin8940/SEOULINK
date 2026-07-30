package com.seoulink.backend.domain.course.dto.response;

import com.seoulink.backend.domain.course.model.TransportMode;
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

    // 저장된 코스의 기본 정보와 공개 상태이다.
    private Long courseId;
    private Long resultId;
    private String title;
    private String description;
    private String coverImageUrl;
    private String travelCode;
    private TransportMode transportMode;
    private String courseType;
    private String region;
    private Boolean publicCourse;
    private Long viewCount;

    // 코스 전체의 장소·일수·거리·시간 집계값이다.
    private Integer placeCount;
    private Integer dayCount;
    private Double totalDistanceKm;
    private Double totalTravelTimeMinutes;
    private Integer totalVisitTimeMinutes;
    private Double totalCourseTimeMinutes;
    private Boolean estimatedTravelTimes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 날짜별로 묶이고 각 날짜 안에서 방문 순서대로 정렬된 장소 목록이다.
    @Builder.Default
    private List<CourseDayResponse> days = new ArrayList<>();
}
