package com.seoulink.backend.domain.course.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 설문 추천 결과를 날짜별 코스로 최적화할 때 사용하는 최종 요청 DTO이다.
 *
 * <p>요청 계약은 설문 결과·일정 시작 시각·날짜별 후보 풀로 구성한다.
 * 코스 저장은 사용자가 추천 결과에서 원하는 옵션을 선택한 뒤
 * 별도 저장 API에서 처리한다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRecommendRequest {

    // 추천의 기준이 된 SURVEY_RESULT 식별자와 5자리 여행 유형 코드이다.
    private Long resultId;
    private String travelCode;

    // 모든 날짜 일정의 공통 시작 시각이다.
    @JsonFormat(pattern = "HH:mm")
    private LocalTime dailyStartTime;

    // 다시 추천할 때 직전 화면에 표시된 코스 조합을 제외하기 위한 서버 발급 키 목록이다.
    @Builder.Default
    private List<String> excludedRecommendationKeys = new ArrayList<>();

    // 날짜별 방문 후보와 장소별 대체 후보를 담은 목록이다.
    @Builder.Default
    private List<DailyPlanRequest> dailyPlans = new ArrayList<>();
}
