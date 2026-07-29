package com.seoulink.backend.domain.course.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 다시 추천받기에서 설문 기준 후보 풀을 DB에서 새로 조회할 때 사용하는 요청 DTO입니다.
 *
 * <p>직전 추천 결과에 등장한 장소 ID는 새 후보 조회에서 우선 제외합니다.
 * 후보가 부족한 카테고리만 기존 장소를 fallback 후보로 함께 전달하고,
 * 최종 중복 허용 여부는 코스 추천 서비스가 판단합니다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class CourseDraftRefreshRequest {

    private Long surveyId;

    private List<Long> previouslyRecommendedPlaceIds = new ArrayList<>();
}
