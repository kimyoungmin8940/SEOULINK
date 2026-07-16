package com.seoulink.backend.domain.course.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 하루 단위 추천 장소 후보를 묶어 전달하는 요청 DTO이다.
 *
 * <p>방문 날짜는 장소마다 반복해서 보내지 않고 날짜 그룹에 한 번만 전달한다.
 * 서비스는 이 날짜를 그룹 안의 모든 원본 장소와 대체 후보에 자동으로 적용한다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPlanRequest {

    // 이 그룹에 포함된 모든 장소가 방문할 실제 날짜이다.
    private LocalDate visitDate;

    // 해당 날짜에 우선 배치할 장소 후보 목록이다.
    @Builder.Default
    private List<PlaceCandidateDto> placeCandidates = new ArrayList<>();
}
