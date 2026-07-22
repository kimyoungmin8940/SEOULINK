package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.domain.course.model.TransportMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 등록된 이동수단 계산기 중 요청한 모드에 맞는 구현을 선택한다. */
@Component
public class RouteCalculatorFactory {

    private final Map<TransportMode, RouteCalculator> calculators =
            new EnumMap<>(TransportMode.class);

    public RouteCalculatorFactory(List<RouteCalculator> routeCalculators) {
        if (routeCalculators == null) {
            throw new IllegalArgumentException("이동수단 계산기 목록은 null일 수 없습니다.");
        }
        for (RouteCalculator calculator : routeCalculators) {
            RouteCalculator previous = calculators.putIfAbsent(
                    calculator.supportedMode(),
                    calculator
            );
            if (previous != null) {
                throw new IllegalStateException(
                        "동일한 이동수단 계산기가 중복 등록되었습니다. mode="
                                + calculator.supportedMode()
                );
            }
        }
    }

    public RouteCalculator get(TransportMode transportMode) {
        if (transportMode == null) {
            throw new IllegalArgumentException("이동수단은 필수입니다.");
        }
        RouteCalculator calculator = calculators.get(transportMode);
        if (calculator == null) {
            throw new IllegalStateException(
                    "이동수단 계산기가 등록되지 않았습니다. mode=" + transportMode
            );
        }
        return calculator;
    }
}
