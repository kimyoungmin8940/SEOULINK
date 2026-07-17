package com.seoulink.backend.domain.survey.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 비회원 설문 제출 요청 DTO이다.
 *
 * <p>여행 기본 정보와 사용자가 선택한 답변 목록을 받는다.</p>
 */
public record GuestSurveySubmitRequest(

        @NotBlank(message = "여행 지역은 필수입니다")
        String region,

        @NotNull(message = "여행 시작일은 필수입니다")
        @FutureOrPresent(
                message = "여행 시작일은 오늘 이후여야 합니다"
        )
        LocalDate startDate,

        @NotNull(message = "여행 종료일은 필수입니다")
        @FutureOrPresent(
                message = "여행 종료일은 오늘 이후여야 합니다"
        )
        LocalDate endDate,

        @NotBlank(message = "여행 동행 유형은 필수입니다")
        String companionType,

        @NotBlank(message = "이동 방식은 필수입니다")
        String transportType,

        @NotEmpty(message = "설문 답변이 비어 있습니다")
        List<@Valid SurveyAnswerRequest> answers

) {

    /**
     * 종료일이 시작일보다 빠른 요청을 차단한다.
     */
    @AssertTrue(
            message = "여행 종료일은 시작일보다 빠를 수 없습니다"
    )
    public boolean isTravelPeriodValid() {
        if (startDate == null || endDate == null) {
            return true;
        }

        return !endDate.isBefore(startDate);
    }

    //여행 기간을 시작일 포함 최대 7일로 제한
    @AssertTrue(
            message = "여행 기간은 최대 7일까지 선택할 수 있습니다"
    )
    public boolean isTravelDurationValid() {
        if (startDate == null || endDate == null) {
            return true;
        }

        if (endDate.isBefore(startDate)) {
            return true;
        }

        long travelDays =
                ChronoUnit.DAYS.between(
                        startDate,
                        endDate
                ) + 1;

        return travelDays <= 7;
    }
}