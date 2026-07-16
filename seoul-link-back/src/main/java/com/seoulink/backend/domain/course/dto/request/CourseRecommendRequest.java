package com.seoulink.backend.domain.course.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 추천 장소 후보를 최적화하고 코스로 저장할 때 사용하는 통합 요청 DTO이다. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRecommendRequest {

    // 회원·설문·결제 도메인과 연결할 식별자이다. 결제 ID는 유료 코스가 아닐 수 있어 선택값이다.
    private Long memberId;
    private Long resultId;
    private Long paymentId;

    // 저장될 코스의 화면 표시 정보와 여행 유형 정보이다.
    private String title;
    private String description;
    private String travelCode;
    private String region;

    // 공개 여부를 생략하면 비공개 코스로 저장한다.
    @Builder.Default
    private Boolean publicCourse = false;

    // 추천 담당자가 계산한 1차 코스 후보를 받아 최적화 서비스로 전달한다.
    @Builder.Default
    private List<PlaceCandidateDto> placeCandidates = new ArrayList<>();

    // 먼 장소가 발견됐을 때 같은 날짜·카테고리 안에서 교체할 예비 후보이다.
    @Builder.Default
    private List<PlaceCandidateDto> alternativeCandidates = new ArrayList<>();
}
