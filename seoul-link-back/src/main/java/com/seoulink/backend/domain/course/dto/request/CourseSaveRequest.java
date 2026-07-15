package com.seoulink.backend.domain.course.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자가 선택한 최적화 코스를 DB에 저장할 때 사용하는 요청 DTO이다.
 *
 * <p>{@code memberId}는 인증 기능 통합 전 서비스 테스트를 위해 포함한다.
 * 로그인 통합 후에는 요청 본문이 아니라 인증 사용자 정보에서 가져오도록 변경한다.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseSaveRequest {

    private Long memberId;
    private Long resultId;
    private Long paymentId;
    private String title;
    private String description;
    private String travelCode;

    @Builder.Default
    private String courseType = "CUSTOM";

    private String region;

    @Builder.Default
    private Boolean publicCourse = false;

    @Builder.Default
    private List<CourseSavePlaceDto> places = new ArrayList<>();
}
