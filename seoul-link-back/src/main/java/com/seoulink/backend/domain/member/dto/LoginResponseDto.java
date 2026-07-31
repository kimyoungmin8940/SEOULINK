package com.seoulink.backend.domain.member.dto;

import lombok.Getter;

@Getter
public class LoginResponseDto {
    private final Long memberId;
    private final String email;
    private final String name;
    private final String loginType;

    public LoginResponseDto(Long memberId, String email, String name, String loginType) {
        this.memberId = memberId;
        this.email = email;
        this.name = name;
        this.loginType = loginType;
    }
}
