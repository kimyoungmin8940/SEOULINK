package com.seoulink.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberSignupRequest {
    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 8, max = 30)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$")
    private String password;

    @NotBlank
    @Size(max = 50)
    private String name;

    @Size(max = 50)
    private String nickname;

    @Size(max = 20)
    @Pattern(regexp = "^[0-9\\-+ ]*$")
    private String phone;
}
