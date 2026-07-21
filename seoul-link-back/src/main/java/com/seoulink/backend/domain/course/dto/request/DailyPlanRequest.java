package com.seoulink.backend.domain.course.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 하루 단위 추천 후보 풀과 최종 선발 기준을 전달하는 요청 DTO이다.
 *
 * <p>{@code targetPlaceCount}만큼 장소를 선발하되, {@code categoryTargets}에 지정된
 * TOUR·RESTAURANT·CAFE·HOTEL 개수를 정확히 맞춘다. 방문 날짜는 장소마다 반복하지
 * 않고 날짜 그룹에 한 번만 전달하며, 서비스가 원본 장소와 대체 후보에 자동 적용한다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyPlanRequest {

    // 이 그룹에 포함된 모든 장소가 방문할 실제 날짜이다.
    private LocalDate visitDate;

    // 이 날짜의 최종 코스에 포함할 장소 수이다.
    private Integer targetPlaceCount;

    // 카테고리별 최종 선발 개수이다. 지원 키: TOUR, RESTAURANT, CAFE, HOTEL.
    @Builder.Default
    private Map<String, Integer> categoryTargets = new LinkedHashMap<>();

    // 선발 전 후보 풀이며, 이 목록에서 서로 다른 3개 코스 조합을 만든다.
    @Builder.Default
    private List<PlaceCandidateDto> placeCandidates = new ArrayList<>();
}
