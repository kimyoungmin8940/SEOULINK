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

    @Builder.Default
    private List<PlaceCandidateDto> placeCandidates = new ArrayList<>();
}
