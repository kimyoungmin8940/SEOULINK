package com.seoulink.backend.service;

import com.seoulink.backend.dto.request.MemberLoginRequest;
import com.seoulink.backend.dto.request.MemberSignupRequest;
import com.seoulink.backend.dto.response.MemberLoginResponse;
import com.seoulink.backend.entity.Member;
import com.seoulink.backend.entity.Payment;
import com.seoulink.backend.repository.MemberRepository;
import com.seoulink.backend.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final boolean demoSignupPassEnabled;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberService(
            MemberRepository memberRepository,
            PaymentRepository paymentRepository,
            @Value("${app.demo-signup-pass-enabled:false}") boolean demoSignupPassEnabled
    ) {
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
        this.demoSignupPassEnabled = demoSignupPassEnabled;
    }

    @Transactional
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
        if (request.getNickname() != null && !request.getNickname().isBlank()
                && memberRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        Member member = new Member(
                request.getLoginId(), request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getName(), request.getNickname()
        );
        Member savedMember = memberRepository.save(member);
        if (demoSignupPassEnabled) grantDemoPass(savedMember);
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

    public boolean checkLoginId(String loginId) { return !memberRepository.existsByLoginId(loginId); }
    public boolean checkEmail(String email) { return !memberRepository.existsByEmail(email); }
    public boolean checkNickname(String nickname) { return !memberRepository.existsByNickname(nickname); }

    private void grantDemoPass(Member member) {
        Payment payment = new Payment();
        payment.setMemberId(member.getMemberId());
        payment.setProductName("개발용 AI 챗봇 7일 체험권");
        payment.setAmount(0);
        payment.setPaymentMethod("DEMO");
        payment.setPaymentProvider("DEMO");
        payment.setOrderId("DEMO_SIGNUP_" + member.getMemberId());
        payment.markPaid("DEMO", 1, LocalDateTime.now().plusDays(7));
        paymentRepository.save(payment);
    }

    private MemberLoginResponse toLoginResponse(Member member) {
        return new MemberLoginResponse(
                member.getMemberId(), member.getLoginId(), member.getEmail(),
                member.getName(), member.getNickname()
        );
    }
}
