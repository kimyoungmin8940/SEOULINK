package com.seoulink.backend.domain.course.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 추천 결과와 상세 화면에서 공통으로 사용하는 날짜별 일정 응답이다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDayResponse {

    // 실제 날짜와 화면에 표시할 1일차·2일차 번호이다.
    private Integer dayNo;
    private LocalDate visitDate;

    // 해당 날짜 안에서 visitOrder 오름차순으로 정렬된 장소 목록이다.
    @Builder.Default
    private List<CoursePlaceResponse> places = new ArrayList<>();
}
