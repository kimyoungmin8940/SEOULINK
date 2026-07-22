package com.seoulink.backend.domain.course.model;

/**
 * 한 여행 코스 전체에 공통으로 적용되는 이동수단이다.
 *
 * <p>JSON과 Oracle DB 모두 enum 이름을 그대로 저장하므로 값 이름을 변경할 때는
 * 요청 계약과 {@code TRANSPORT_MODE} 체크 제약조건도 함께 변경해야 한다.</p>
 */
public enum TransportMode {
    WALKING,
    PUBLIC_TRANSIT,
    DRIVING
}
