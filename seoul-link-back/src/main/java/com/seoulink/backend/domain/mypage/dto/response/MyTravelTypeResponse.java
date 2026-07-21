package com.seoulink.backend.domain.mypage.dto.response;

import lombok.Getter;

@Getter
public class MyTravelTypeResponse {
    private String travelCode;
    private String typeTitle;
    private String typeDescription;
    private String imageUrl;

    public MyTravelTypeResponse(String travelCode, String typeTitle, String typeDescription, String imageUrl) {
        this.travelCode = travelCode;
        this.typeTitle = typeTitle;
        this.typeDescription = typeDescription;
        this.imageUrl = imageUrl;
    }
}