package com.seoulink.backend.domain.course.service;

import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 장소 카테고리에 따라 예상 체류시간을 계산한다.
 *
 * <p>현재는 DB 통합 전이므로 카테고리별 기본값을 사용한다.
 * 추후 장소별 기본 체류시간이 DB에 저장되면 이 서비스의 구현만 교체할 수 있다.</p>
 */
@Service
public class VisitDurationService {

    private static final int ATTRACTION_MINUTES = 90;
    private static final int RESTAURANT_MINUTES = 60;
    private static final int CAFE_MINUTES = 60;
    private static final int ACCOMMODATION_MINUTES = 30;
    private static final int DEFAULT_MINUTES = 60;

    /**
     * 장소 카테고리에 맞는 예상 방문 시간을 반환한다.
     *
     * @param category 장소 대분류
     * @return 예상 방문 시간(분)
     */
    public int calculateExpectedVisitMinutes(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("장소 카테고리는 필수입니다.");
        }

        String normalizedCategory = category.trim().toLowerCase(Locale.ROOT);

        return switch (normalizedCategory) {
            case "관광지", "attraction", "tourist_attraction" -> ATTRACTION_MINUTES;
            case "식당", "음식점", "restaurant" -> RESTAURANT_MINUTES;
            case "카페", "cafe", "café" -> CAFE_MINUTES;
            case "숙소", "호텔", "accommodation", "lodging", "hotel" ->
                    ACCOMMODATION_MINUTES;
            default -> DEFAULT_MINUTES;
        };
    }
}
