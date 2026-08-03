package com.seoulink.backend.domain.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberSignupRequest {
    @NotBlank(message = "Password is required.")
    @Size(
            min = 8,
            max = 30,
            message = "비밀번호는 8자 이상 30자 이하로 입력해주세요."
    )
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,30}$",
            message = "비밀번호는 영문과 숫자를 반드시 포함해야 합니다."
    )
    private String password;

    @NotBlank(message = "Name is required.")
    @Size(max = 50)
    private String name;

    @Size(max = 50)
    private String nickname;

    @NotBlank(message = "Email is required.")
    @Email(message = "Email format is invalid.")
    @Size(max = 100)
    private String email;

    @NotBlank(message = "휴대폰 번호를 입력해주세요.")
    @Pattern(
            regexp = "^\\d{11}$",
            message = "휴대폰 번호는 숫자 11자리로 입력해주세요."
    )
    private String phone;
}
