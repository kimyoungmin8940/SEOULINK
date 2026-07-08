package com.seoulink.backend.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class MyPageResponse {
    private MemberLoginResponse member;
    private MyTravelTypeResponse travelType;
    private List<MyCourseResponse> courses;
    private List<?> payments;
    private List<?> chatbotHistories;
    private List<ReviewResponse> reviews;

    public MyPageResponse(
            MemberLoginResponse member,
            MyTravelTypeResponse travelType,
            List<MyCourseResponse> courses,
            List<?> payments,
            List<?> chatbotHistories,
            List<ReviewResponse> reviews
    ) {
        this.member = member;
        this.travelType = travelType;
        this.courses = courses;
        this.payments = payments;
        this.chatbotHistories = chatbotHistories;
        this.reviews = reviews;
    }
}
