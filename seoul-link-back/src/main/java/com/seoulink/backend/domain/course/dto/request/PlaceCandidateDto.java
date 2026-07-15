package com.seoulink.backend.domain.course.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 코스 최적화의 입력으로 전달되는 장소 후보 한 건을 표현한다.
 *
 * <p>추천 담당자가 계산한 추천 점수와 장소의 좌표를 함께 전달받아
 * 날짜별 방문 순서를 정할 때 사용한다. {@code placeId}는 외부 지도 API의
 * 장소 ID가 아니라 SEOULINK의 {@code PLACES} 테이블에서 사용하는 내부 ID를 기준으로 한다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceCandidateDto {

    private Long placeId;
    private String placeName;
    private String category;
    private Double recommendationScore;
    private Double latitude;
    private Double longitude;
    private LocalDate visitDate;
    private Integer expectedVisitMinutes;
}
