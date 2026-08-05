package com.seoulink.backend.domain.member.controller;

import com.seoulink.backend.domain.member.dto.request.MemberLoginRequest;
import com.seoulink.backend.domain.member.dto.request.MemberSignupRequest;
import com.seoulink.backend.domain.member.dto.request.PasswordResetRequest;
import com.seoulink.backend.domain.member.dto.request.PasswordResetVerifyRequest;
import com.seoulink.backend.domain.member.dto.response.MemberLoginResponse;
import com.seoulink.backend.domain.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;


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

    @PostMapping("/password-reset/verify")
    public boolean verifyPasswordResetMember(@Valid @RequestBody PasswordResetVerifyRequest request) {
        return memberService.verifyPasswordResetMember(request);
    }

    @PostMapping("/password-reset")
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        memberService.resetPassword(request);
    }
    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> withdrawMember(
            @PathVariable Long memberId
    ) {
        memberService.withdrawMember(memberId);

        return ResponseEntity.noContent().build();
    }

}
