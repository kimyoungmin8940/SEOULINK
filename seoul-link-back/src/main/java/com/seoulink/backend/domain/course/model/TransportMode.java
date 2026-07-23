package com.seoulink.backend.domain.course.model;

import java.util.Locale;

/**
 * 한 여행 코스 전체에 공통으로 적용되는 이동수단이다.
 *
 * <p>코스 API는 enum 이름을 사용하고, Oracle DB의 설문 테이블은 기존 값인
 * PUBLIC/WALKING/CAR를 저장하므로 {@link #fromSurveyTransportType(String)}에서
 * 두 표현을 한곳에서 변환한다.</p>
 */
public enum TransportMode {
    WALKING,
    PUBLIC_TRANSIT,
    DRIVING;

    /**
     * TRAVEL_SURVEY.TRANSPORT_TYPE 값을 코스 API에서 사용하는 이동수단 값으로 변환한다.
     *
     * <p>설문 화면의 기존 값(PUBLIC/WALKING/CAR)과 이미 API enum 이름으로 저장된
     * 호환 값을 모두 처리한다. 알 수 없는 값은 저장 데이터 오류를 숨기지 않도록 null로
     * 반환하며, 호출부가 이동수단 미확정 상태로 표시할 수 있게 한다.</p>
     */
    public static TransportMode fromSurveyTransportType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PUBLIC", "PUBLIC_TRANSIT" -> PUBLIC_TRANSIT;
            case "WALKING" -> WALKING;
            case "CAR", "DRIVING" -> DRIVING;
            default -> null;
        };
    }
}
