package com.seoulink.backend.domain.course.dto.response;

import com.seoulink.backend.domain.place.dto.response.PlaceRecommendationResponse;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 여행 날짜 하루에 해당하는 추천 코스 후보를 전달하는 응답 DTO입니다.
 *
 * 해당 날짜, 최종적으로 선택할 목표 장소 수,
 * 카테고리별 후보 개수와 추천 장소 후보 목록을 담습니다.
 *
 * 기본 추천 장소는 날짜 간 중복되지 않도록 분배하며,
 * 각 장소의 추천 점수, 좌표, 테마 정보와 대체 후보는 그대로 전달합니다.
 *
 * 이 목록은 방문 순서가 정해진 최종 코스가 아니라,
 * 다음 단계에서 최종 장소를 선택하고 이동 순서를 최적화하기 위한 후보군입니다.
 */

@Getter
public class DailyCourseDraftResponse {
    // 후보 장소를 방문할 날짜
    private final LocalDate visitDate;

    // 해당 날짜에 최종적으로 선택할 목표 장소 수
    private final int targetPlaceCount;

    // 최종 장소를 선택하기 전에 전달하는 카테고리별 후보 개수
    // P형 예: {"TOUR": 7, "RESTAURANT": 4, "CAFE": 4, "HOTEL": 0}
    // R형 예: {"TOUR": 4, "RESTAURANT": 3, "CAFE": 3, "HOTEL": 0}
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
