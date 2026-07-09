package com.seoulink.backend.service;

import com.seoulink.backend.dto.request.MemberLoginRequest;
import com.seoulink.backend.dto.request.MemberSignupRequest;
import com.seoulink.backend.dto.response.MemberLoginResponse;
import com.seoulink.backend.entity.Member;
import com.seoulink.backend.repository.MemberRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public MemberLoginResponse signup(MemberSignupRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }

        if (memberRepository.existsByLoginId(request.getLoginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        if (request.getNickname() != null
                && !request.getNickname().isBlank()
                && memberRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        Member member = new Member(
                request.getLoginId(),
                request.getEmail(),
                encodedPassword,
                request.getName(),
                request.getNickname()
        );

        Member savedMember = memberRepository.save(member);

        return toLoginResponse(savedMember);
    }

    public MemberLoginResponse login(MemberLoginRequest request) {
        Member member = memberRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!"LOCAL".equals(member.getLoginType())) {
            throw new IllegalArgumentException("소셜 로그인으로 가입된 계정입니다.");
        }

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!"ACTIVE".equals(member.getStatus())) {
            throw new IllegalArgumentException("사용할 수 없는 계정입니다.");
        }

        return toLoginResponse(member);
    }

    public boolean checkLoginId(String loginId) {
        return !memberRepository.existsByLoginId(loginId);
    }

    public boolean checkEmail(String email) {
        return !memberRepository.existsByEmail(email);
    }

    public boolean checkNickname(String nickname) {
        return !memberRepository.existsByNickname(nickname);
    }

    private MemberLoginResponse toLoginResponse(Member member) {
        return new MemberLoginResponse(
                member.getMemberId(),
                member.getLoginId(),
                member.getEmail(),
                member.getName(),
                member.getNickname()
        );
    }
}