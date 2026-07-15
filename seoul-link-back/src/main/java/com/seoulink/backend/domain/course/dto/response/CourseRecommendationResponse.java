package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 추천 결과 목록과 메인 화면의 코스 카드에 사용하는 응답 DTO이다.
 *
 * <p>최적화 계산 결과에 코스 제목, 설명, 대표 이미지, 지역, 태그처럼
 * 화면에 필요한 정보를 보강한 뒤 사용한다. 주소·이미지·태그는 장소 DB가
 * 연결된 이후 채우며, {@code courseId}는 코스 저장 전에는 null일 수 있다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRecommendationResponse {

    private Long courseId;
    private String title;
    private String description;
    private String coverImageUrl;

    @Builder.Default
    private List<String> regions = new ArrayList<>();

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    private Integer placeCount;
    private Integer dayCount;
    private Double totalDistanceKm;
    private Double totalTravelTimeMinutes;
    private Integer totalVisitTimeMinutes;
    private Double totalCourseTimeMinutes;
    private Boolean liked;
}
