package com.seoulink.backend.domain.course.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.seoulink.backend.domain.course.model.TransportMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 1번 담당자가 날짜를 배정한 코스 초안을 받아 방문 순서와 먼 장소 교체를 계산하는 요청 DTO이다.
 *
 * <p>각 장소의 {@code alternativeCandidates}에는 해당 장소가 먼 이동 구간으로 판정됐을 때만
 * 사용할 전용 대체 후보를 담는다. 대체 후보는 {@code visitDate}를 생략할 수 있으며,
 * 실제 교체 시 부모 장소의 방문 날짜를 자동으로 상속한다.</p>
 *
 * <p>{@code alternativeCandidates} 최상위 필드는 이전 테스트·호출부와의 호환을 위해 남겨두며,
 * 신규 요청은 반드시 각 장소 내부의 중첩 필드를 사용한다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOptimizeRequest {

    // 설문 결과와 여행 유형을 최적화 요청까지 추적하기 위한 전달 메타데이터이다.
    private Long resultId;
    private String travelCode;

    // 방문 순서·먼 장소 교체·대체 후보 평가에 끝까지 적용할 공통 이동수단이다.
    private TransportMode transportMode;

    // 식사시간 배치가 확정되기 전에는 전달만 받고 현재 거리 최적화 계산에는 사용하지 않는다.
    @JsonFormat(pattern = "HH:mm")
    private LocalTime dailyStartTime;

    /**
     * 이전 프런트 요청과의 JSON 호환을 위해 남겨 둔 필드이다.
     * 대중교통 40분 상한은 안전 규칙이므로 false가 전달돼도 기본 보정은 유지한다.
     */
    @Deprecated
    @Builder.Default
    private Boolean enforcePublicTransitLimit = Boolean.TRUE;

    /**
     * 기존 40분 제한 보정을 모두 시도한 뒤, 현재 장소와 순서를 보존한 실제 구간만
     * 조회하는 최종 폴백인지 명시한다. 기본값은 false이므로 (3)의 기존 동작은 바뀌지 않는다.
     */
    @Builder.Default
    private Boolean preserveOriginalPublicTransitRoute = Boolean.FALSE;

    /**
     * 대중교통 후보 교체를 모두 시도한 뒤 장소 수 감소까지 허용할지 여부이다.
     * 프런트는 후보 풀을 단계적으로 넓히는 동안 false, 마지막 단계에서 true를 보낸다.
     */
    @Builder.Default
    private Boolean allowPublicTransitPlaceReduction = Boolean.TRUE;

    // 실제 코스에 배치할 장소 목록이며, 각 장소 내부에 전용 대체 후보를 포함한다.
    @Builder.Default
    private List<PlaceCandidateDto> placeCandidates = new ArrayList<>();

    /**
     * 이전 최상위 대체 후보 요청과의 임시 호환 필드이다.
     * 신규 JSON에서는 사용하지 않고 {@code placeCandidates[].alternativeCandidates}를 사용한다.
     */
    @Deprecated
    @Builder.Default
    private List<PlaceCandidateDto> alternativeCandidates = new ArrayList<>();
}
