package com.seoulink.backend.domain.course.dto.request;

import com.seoulink.backend.domain.course.model.TransportMode;
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

    // 코스 소유 회원과 선택적으로 연결되는 설문·결제 식별자이다.
    private Long memberId;
    private Long resultId;
    private Long paymentId;

    // 상세 화면과 코스 카드에 표시할 기본 정보이다.
    private String title;
    private String description;
    private String travelCode;

    // 추천 계산·검증에 사용하며, SURVEY 코스 조회 시에는 resultId로 연결된
    // TRAVEL_SURVEY.TRANSPORT_TYPE 값을 다시 읽어 같은 이동수단을 복원한다.
    private TransportMode transportMode;

    // CUSTOM(직접 생성), SURVEY(취향 추천), CHATBOT(AI 생성) 중 하나이다.
    @Builder.Default
    private String courseType = "CUSTOM";

    // THEME 코스를 저장할 때 사용하는 원본 코스 식별값
    private String sourceCourseKey;

    private String region;

    // 별도 지정이 없으면 회원의 비공개 코스로 저장한다.
    @Builder.Default
    private Boolean publicCourse = false;

    // 최적화가 끝난 장소를 날짜·방문 순서와 함께 전달한다.
    @Builder.Default
    private List<CourseSavePlaceDto> places = new ArrayList<>();
}
