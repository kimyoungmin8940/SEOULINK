package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;

import java.util.List;

/** 추천 서비스가 외부 경로 제공자와 무관하게 이동수단별 행렬을 요청하는 계약이다. */
public interface RouteCalculator {

    TransportMode supportedMode();

    RouteCalculation calculate(List<RouteCoordinate> coordinates);

    /**
     * 대규모 후보 풀을 외부 API 호출 없이 1차 평가할 때 사용할 추정 행렬을 반환한다.
     * 별도 추정 구현이 필요 없는 계산기는 기존 계산 방식을 그대로 사용할 수 있다.
     */
    default RouteCalculation estimate(List<RouteCoordinate> coordinates) {
        return calculate(coordinates);
    }

    /**
     * 캐시에 없는 방향별 장소 쌍만 계산할 수 있도록 필요한 행렬 위치를 함께 전달한다.
     * 행렬 API를 사용하는 구현은 기본적으로 전체 행렬을 한 번 계산해도 된다.
     */
    default RouteCalculation calculate(
            List<RouteCoordinate> coordinates,
            boolean[][] requiredPairs
    ) {
        return calculate(coordinates);
    }
}
