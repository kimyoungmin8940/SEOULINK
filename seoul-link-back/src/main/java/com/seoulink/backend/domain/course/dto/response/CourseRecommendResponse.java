package com.seoulink.backend.domain.course.dto.response;

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

/** 동일한 후보 풀에서 생성한 세 가지 추천 코스를 반환한다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRecommendResponse {

    // 요청과 결과를 연결하는 설문 결과 식별자·여행 유형 코드·공통 시작 시각이다.
    private Long resultId;
    private String travelCode;
    private TransportMode transportMode;

    @Builder.Default
    private List<String> preferredRegions = new ArrayList<>();

    // true이면 외부 경로 API가 아닌 임시 추정 이동시간이 하나 이상 포함되었다.
    private Boolean estimatedTravelTimes;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime dailyStartTime;

    // 기본적으로 취향 우선·이동 최소·균형 코스 세 가지를 반환한다.
    private Integer optionCount;

    @Builder.Default
    private List<CourseOptionResponse> courseOptions = new ArrayList<>();
}
