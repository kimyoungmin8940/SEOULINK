package com.seoulink.backend.domain.member.dto.response;

import lombok.Getter;

@Getter
public class MemberLoginResponse {
    private final Long memberId;
    private final String email;
    private final String name;
    private final String nickname;

    public MemberLoginResponse(Long memberId, String email, String name, String nickname) {
        this.memberId = memberId;
        this.email = email;
        this.name = name;
        this.nickname = nickname;
    }
}
