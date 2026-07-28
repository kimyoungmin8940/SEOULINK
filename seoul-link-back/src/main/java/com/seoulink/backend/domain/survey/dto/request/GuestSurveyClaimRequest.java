package com.seoulink.backend.domain.survey.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GuestSurveyClaimRequest(
        @NotBlank String guestToken,
        @NotNull @Positive Long memberId
) {
}
