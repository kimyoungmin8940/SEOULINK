package com.seoulink.backend.dto;

public class SurveyResultDto {
    private String typeCode;
    private String description;

    public SurveyResultDto() {
    }

    public SurveyResultDto(String typeCode, String description) {
        this.typeCode = typeCode;
        this.description = description;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getDescription() {
        return description;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}