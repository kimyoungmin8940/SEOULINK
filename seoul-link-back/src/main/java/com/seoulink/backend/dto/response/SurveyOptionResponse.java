package com.seoulink.backend.dto.response;

import com.seoulink.backend.entity.SurveyOption;
import lombok.Getter;

@Getter
public class SurveyOptionResponse {
    private Long optionId;
    private String optionText;

    public SurveyOptionResponse(SurveyOption option) {
        this.optionId = option.getOptionId();
        this.optionText = option.getOptionText();
    }
}