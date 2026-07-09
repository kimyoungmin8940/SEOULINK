package com.seoulink.backend.dto.response;

import lombok.Getter;

@Getter
public class MemberLoginResponse {
    private Long memberId;
    private String loginId;
    private String email;
    private String name;
    private String nickname;

    public MemberLoginResponse(Long memberId, String loginId, String email, String name, String nickname) {
        this.memberId = memberId;
        this.loginId = loginId;
        this.email = email;
        this.name = name;
        this.nickname = nickname;
    }
}