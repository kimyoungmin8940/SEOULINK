package com.seoulink.backend.domain.course.dto.response;

import com.seoulink.backend.domain.course.model.TransportMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 추천 결과 목록과 메인 화면의 코스 카드에 사용하는 응답 DTO이다.
 *
 * <p>저장 코스와 COURSE_DETAILS를 조회한 뒤 PLACES의 대표 이미지·지역·테마를
 * 보강해 목록 카드에 필요한 정보를 반환한다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRecommendationResponse {

    // 추천 카드와 목록 화면에 표시할 코스 기본 정보이다.
    private Long courseId;
    private Long resultId;
    private String recommendationKey;
    private String title;
    private String description;
    private String coverImageUrl;
    @Builder.Default
    private List<String> coverImageUrls = new ArrayList<>();
    private String courseType;
    private TransportMode transportMode;

    @Builder.Default
    private List<String> regions = new ArrayList<>();

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    // 카드에서 소요 규모를 보여 주기 위한 코스 집계값이다.
    private Integer placeCount;
    private Integer dayCount;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double totalDistanceKm;
    private Double totalTravelTimeMinutes;
    private Integer totalVisitTimeMinutes;
    private Double totalCourseTimeMinutes;
    private LocalDateTime createdAt;

    // 하트 도메인 연동 전에는 조회 서비스가 false로 채우는 임시 필드이다.
    private Boolean liked;
}
