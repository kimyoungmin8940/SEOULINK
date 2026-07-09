package com.seoulink.backend.controller;

import com.seoulink.backend.dto.request.MemberLoginRequest;
import com.seoulink.backend.dto.request.MemberSignupRequest;
import com.seoulink.backend.dto.response.MemberLoginResponse;
import com.seoulink.backend.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/check-login-id")
    public boolean checkLoginId(@RequestParam String loginId) {
        return memberService.checkLoginId(loginId);
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