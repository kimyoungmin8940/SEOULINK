package com.seoulink.backend.domain.course.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisitDurationServiceTest {

    private VisitDurationService visitDurationService;

    @BeforeEach
    void setUp() {
        visitDurationService = new VisitDurationService();
    }

    @Test
    @DisplayName("DB의 4개 기본 카테고리에 따라 예상 방문 시간을 계산한다")
    void calculateExpectedVisitMinutesByCategory() {
        assertEquals(90, visitDurationService.calculateExpectedVisitMinutes("TOUR"));
        assertEquals(60, visitDurationService.calculateExpectedVisitMinutes("RESTAURANT"));
        assertEquals(60, visitDurationService.calculateExpectedVisitMinutes("CAFE"));
        assertEquals(30, visitDurationService.calculateExpectedVisitMinutes("HOTEL"));
    }

    @Test
    @DisplayName("기존 한글 카테고리 표현도 함께 지원한다")
    void supportKoreanCategoryAliases() {
        assertEquals(90, visitDurationService.calculateExpectedVisitMinutes("관광지"));
        assertEquals(60, visitDurationService.calculateExpectedVisitMinutes("식당"));
        assertEquals(60, visitDurationService.calculateExpectedVisitMinutes("카페"));
        assertEquals(30, visitDurationService.calculateExpectedVisitMinutes("숙소"));
    }

    @Test
    @DisplayName("알 수 없는 카테고리는 기본 방문 시간 60분을 사용한다")
    void useDefaultMinutesForUnknownCategory() {
        assertEquals(60, visitDurationService.calculateExpectedVisitMinutes("기타"));
    }

    @Test
    @DisplayName("빈 카테고리는 예외가 발생한다")
    void rejectBlankCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> visitDurationService.calculateExpectedVisitMinutes(" ")
        );
    }
}
