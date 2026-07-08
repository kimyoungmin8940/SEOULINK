package com.seoulink.backend.service;

import com.seoulink.backend.dto.request.PaymentConfirmRequest;
import com.seoulink.backend.dto.request.PaymentCreateRequest;
import com.seoulink.backend.entity.Payment;
import com.seoulink.backend.repository.MemberRepository;
import com.seoulink.backend.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;

    public PaymentService(PaymentRepository paymentRepository, MemberRepository memberRepository) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Payment readyPayment(PaymentCreateRequest request) {
        validateMemberExists(request.getMemberId());

        Payment payment = new Payment();
        payment.setMemberId(request.getMemberId());
        payment.setProductName(request.getProductName());
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentProvider(request.getPaymentProvider());
        payment.setOrderId(request.getOrderId());
        payment.setPaymentStatus("READY");
        payment.setRemainCount(0);

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment confirmPayment(PaymentConfirmRequest request) {
        validateProviderApproval(request);

        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("결제 요청을 찾을 수 없습니다."));

        if (!"READY".equals(payment.getPaymentStatus())) {
            throw new IllegalStateException("승인 가능한 결제 상태가 아닙니다.");
        }

        int remainCount = request.getRemainCount() == null ? 10 : request.getRemainCount();
        int validDays = request.getValidDays() == null ? 30 : request.getValidDays();
        payment.markPaid(request.getPaymentKey(), remainCount, LocalDateTime.now().plusDays(validDays));

        return payment;
    }

    @Transactional
    public Payment createPayment(PaymentCreateRequest request) {
        Payment payment = readyPayment(request);
        payment.markPaid(
                request.getPaymentKey(),
                10,
                LocalDateTime.now().plusDays(30)
        );
        return payment;
    }

    public List<Payment> getPayments(Long memberId) {
        validateMemberExists(memberId);
        return paymentRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Transactional
    public Payment cancelPayment(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        payment.markCanceled(reason);

        return payment;
    }

    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
    }

    private void validateProviderApproval(PaymentConfirmRequest request) {
        if (request.getPaymentKey() == null || request.getPaymentKey().isBlank()) {
            throw new IllegalArgumentException("결제 승인 정보가 필요합니다.");
        }
    }
}