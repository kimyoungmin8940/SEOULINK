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
 * 날짜별 방문 순서를 정할 때 사용한다. 예상 방문 시간은 요청으로 받지 않고
 * 최적화 단계에서 카테고리에 따라 계산한다. {@code placeId}는 외부 지도 API의
 * 장소 ID가 아니라 SEOULINK의 {@code PLACES} 테이블에서 사용하는 내부 ID를 기준으로 한다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceCandidateDto {

    // 장소 DB의 식별자와 화면·체류시간 계산에 필요한 기본 정보이다.
    private Long placeId;
    private String placeName;
    private String category;

    // 추천 담당자가 계산한 점수로 첫 장소와 경로 비용 동점 후보를 결정한다.
    private Double recommendationScore;

    // 거리 계산 좌표와 추천 담당자가 우선 배정한 방문 날짜이다.
    private Double latitude;
    private Double longitude;
    private LocalDate visitDate;
}
