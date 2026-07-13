package com.seoulink.backend.controller;

import com.seoulink.backend.dto.request.MemberLoginRequest;
import com.seoulink.backend.dto.request.MemberSignupRequest;
import com.seoulink.backend.dto.response.MemberLoginResponse;
import com.seoulink.backend.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/signup")
    public MemberLoginResponse signup(@Valid @RequestBody MemberSignupRequest request) {
        return memberService.signup(request);
    }

    @PostMapping("/login")
    public MemberLoginResponse login(@Valid @RequestBody MemberLoginRequest request) {
        return memberService.login(request);
    }

    @GetMapping("/check-email")
    public boolean checkEmail(@RequestParam String email) {
        return memberService.checkEmail(email);
    }

    @GetMapping("/check-nickname")
    public boolean checkNickname(@RequestParam String nickname) {
        return memberService.checkNickname(nickname);
    }
}
