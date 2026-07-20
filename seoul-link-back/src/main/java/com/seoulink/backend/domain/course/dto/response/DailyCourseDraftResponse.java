package com.seoulink.backend.domain.course.dto.response;

import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 여행 날짜 하루에 해당하는 추천 코스 후보를 전달하는 응답 DTO
 *
 * 해당 날짜, 하루 목표 장소 수, 카테고리별 목표 개수와
 * 최종 코스를 만들 때 사용할 추천 장소 후보 목록을 담음
 *
 * 기본 추천 장소의 중복은 날짜별로 제거하지만,
 * 각 장소에 포함된 추천 점수, 좌표, 테마 정보와 대체 후보는 그대로 전달
 *
 * 이 클래스의 장소 후보는 아직 방문 순서가 정해진 최종 코스가 아니라
 * 다음 단계에서 최종 장소 선택과 이동 순서 최적화에 사용하는 후보군
 */

@Getter
public class DailyCourseDraftResponse {
    // 후보 장소를 방문할 날짜
    private final LocalDate visitDate;

    // 해당 날짜에 최종적으로 선택할 목표 장소 수
    private final int targetPlaceCount;

    // 카테고리별 목표 장소 수
    // 예: {"TOUR": 3, "RESTAURANT": 2, "CAFE": 1}
    private final Map<String, Integer> categoryTargets;

    // 최종 장소 선택에 사용할 넉넉한 추천 후보 목록
    private final List<PlaceRecommendationResponse> placeCandidates;

    public DailyCourseDraftResponse(
            LocalDate visitDate,
            int targetPlaceCount,
            Map<String, Integer> categoryTargets,
            List<PlaceRecommendationResponse> placeCandidates
    ) {
        this.visitDate = visitDate;
        this.targetPlaceCount = targetPlaceCount;
        this.categoryTargets = categoryTargets;
        this.placeCandidates = placeCandidates;
    }
}
