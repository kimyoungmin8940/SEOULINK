package com.seoulink.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberSignupRequest {
    @NotBlank(message = "Password is required.")
    @Size(min = 8, max = 30, message = "Password must be between 8 and 30 characters.")
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

    @Size(max = 20)
    private String phone;
}
