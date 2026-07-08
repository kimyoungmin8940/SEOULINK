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
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        if (request.getNickname() != null && memberRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        Member member = new Member(
                request.getEmail(),
                encodedPassword,
                request.getName(),
                request.getNickname(),
                request.getPhone()
        );

        Member savedMember = memberRepository.save(member);

        return new MemberLoginResponse(
                savedMember.getMemberId(),
                savedMember.getEmail(),
                savedMember.getName(),
                savedMember.getNickname()
        );
    }

    public MemberLoginResponse login(MemberLoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!"ACTIVE".equals(member.getStatus())) {
            throw new IllegalArgumentException("사용할 수 없는 계정입니다.");
        }

        return new MemberLoginResponse(
                member.getMemberId(),
                member.getEmail(),
                member.getName(),
                member.getNickname()
        );
    }

    public boolean checkEmail(String email) {
        return !memberRepository.existsByEmail(email);
    }

    public boolean checkNickname(String nickname) {
        return !memberRepository.existsByNickname(nickname);
    }
}
