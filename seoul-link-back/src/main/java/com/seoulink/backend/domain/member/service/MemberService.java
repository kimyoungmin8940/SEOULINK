package com.seoulink.backend.domain.member.service;

import com.seoulink.backend.domain.member.dto.request.MemberLoginRequest;
import com.seoulink.backend.domain.member.dto.request.MemberSignupRequest;
import com.seoulink.backend.domain.member.dto.LoginResponseDto;
import com.seoulink.backend.domain.member.dto.response.MemberLoginResponse;
import com.seoulink.backend.domain.member.entity.Member;
import com.seoulink.backend.domain.payment.entity.Payment;
import com.seoulink.backend.domain.member.repository.MemberRepository;
import com.seoulink.backend.domain.payment.repository.PaymentRepository;
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

    public MemberService(MemberRepository memberRepository, PaymentRepository paymentRepository,
                         @Value("${app.demo-signup-pass-enabled:false}") boolean demoSignupPassEnabled) {
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
        this.demoSignupPassEnabled = demoSignupPassEnabled;
    }

    @Transactional
    public MemberLoginResponse signup(MemberSignupRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("This email is already registered.");
        }
        if (request.getNickname() != null && !request.getNickname().isBlank()
                && memberRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("This nickname is already in use.");
        }

        Member member = new Member(request.getEmail(), passwordEncoder.encode(request.getPassword()),
                request.getName(), request.getNickname());
        member.setPhone(request.getPhone());
        Member savedMember = memberRepository.save(member);
        if (demoSignupPassEnabled) grantDemoPass(savedMember);
        return toLoginResponse(savedMember);
    }

    public MemberLoginResponse login(MemberLoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Email or password is incorrect."));
        if (!"LOCAL".equals(member.getLoginType())) {
            throw new IllegalArgumentException("Please use the social login method for this account.");
        }
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("Email or password is incorrect.");
        }
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new IllegalArgumentException("This account is inactive.");
        }
        return toLoginResponse(member);
    }

    public boolean checkEmail(String email) { return !memberRepository.existsByEmail(email); }
    public boolean checkNickname(String nickname) { return !memberRepository.existsByNickname(nickname); }

    @Transactional
    public LoginResponseDto socialLogin(String provider, String email, String name) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("The OAuth provider did not provide an email address.");
        }
        Member member = memberRepository.findByEmail(email).orElseGet(() -> {
            Member socialMember = new Member();
            socialMember.setEmail(email);
            socialMember.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            socialMember.setName(name == null || name.isBlank() ? provider + " user" : name);
            socialMember.setStatus("ACTIVE");
            socialMember.setLoginType("SOCIAL");

            return memberRepository.save(socialMember);
        });
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new IllegalArgumentException("This account is inactive.");
        }
        return new LoginResponseDto(member.getMemberId(), member.getEmail(), member.getName(), member.getLoginType());
    }

    private void grantDemoPass(Member member) {
        Payment payment = new Payment();
        payment.setMemberId(member.getMemberId());
        payment.setProductName("Development AI chatbot seven-day pass");
        payment.setAmount(0);
        payment.setPaymentMethod("DEMO");
        payment.setPaymentProvider("DEMO");
        payment.setOrderId("DEMO_SIGNUP_" + member.getMemberId());
        payment.markPaid("DEMO", LocalDateTime.now().plusDays(7));
        paymentRepository.save(payment);
    }

    private MemberLoginResponse toLoginResponse(Member member) {
        return new MemberLoginResponse(member.getMemberId(), member.getEmail(), member.getName(), member.getNickname());
    }
}
