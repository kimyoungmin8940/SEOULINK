package com.seoulink.backend.domain.course.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 추천 장소 후보의 방문 순서를 최적화할 때 사용하는 요청 DTO이다.
 *
 * <p>회원 ID나 설문 결과 ID처럼 DB 저장에 필요한 값은 DB 통합 단계에서 추가하고,
 * 현재는 거리 계산과 방문 순서 정렬에 필요한 장소 후보만 전달받는다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOptimizeRequest {

    // 실제 코스에 우선 배치할 장소 목록이다.
    @Builder.Default
    private List<PlaceCandidateDto> placeCandidates = new ArrayList<>();

    /**
     * 이동거리 2km 또는 이동시간 30분을 초과한 장소를 바꿀 때 사용할 예비 후보이다.
     *
     * <p>대체 후보는 교체 대상과 방문 날짜·기본 카테고리가 같아야 하며,
     * 실제 코스 장소와 중복되지 않는 후보만 사용한다. 후보가 없거나 기준을 만족하는
     * 후보가 없으면 원래 장소를 유지한다.</p>
     */
    @Builder.Default
    private List<PlaceCandidateDto> alternativeCandidates = new ArrayList<>();
}
