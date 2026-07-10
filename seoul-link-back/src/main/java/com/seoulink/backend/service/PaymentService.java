package com.seoulink.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
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
    private final PortOneClient portOneClient;

    public PaymentService(
            PaymentRepository paymentRepository,
            MemberRepository memberRepository,
            PortOneClient portOneClient
    ) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
        this.portOneClient = portOneClient;
    }

    @Transactional
    public Payment readyPayment(PaymentCreateRequest request) {
        validateMemberExists(request.getMemberId());
        validatePlan(request.getAmount());

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
        Payment order = paymentRepository.findByOrderId(request.getPaymentId())
                .orElseThrow(() -> new IllegalArgumentException("결제 주문을 찾을 수 없습니다."));

        if (!"READY".equals(order.getPaymentStatus())) {
            throw new IllegalStateException("승인 가능한 결제 상태가 아닙니다.");
        }

        JsonNode portOnePayment = portOneClient.getPayment(request.getPaymentId());
        validatePortOnePayment(order, portOnePayment);

        int validDays = validatePlan(order.getAmount());
        String transactionId = portOnePayment.path("transactionId").asText(request.getPaymentId());
        order.markPaid(transactionId, 1, LocalDateTime.now().plusDays(validDays));
        return order;
    }

    /** 이전 임시 결제 엔드포인트가 검증 없이 PAID를 만들지 못하게 READY 주문만 생성한다. */
    @Transactional
    public Payment createPayment(PaymentCreateRequest request) {
        return readyPayment(request);
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

    private void validatePortOnePayment(Payment order, JsonNode payment) {
        if (!"PAID".equals(payment.path("status").asText())) {
            throw new IllegalStateException("포트원 결제가 완료되지 않았습니다.");
        }
        if (!portOneClient.getStoreId().equals(payment.path("storeId").asText())) {
            throw new IllegalStateException("결제 상점 정보가 일치하지 않습니다.");
        }
        if (!"KRW".equals(payment.path("currency").asText())) {
            throw new IllegalStateException("결제 통화가 일치하지 않습니다.");
        }
        if (order.getAmount() != payment.path("amount").path("total").asInt()) {
            throw new IllegalStateException("주문 금액과 실제 결제 금액이 일치하지 않습니다.");
        }
    }

    private int validatePlan(int amount) {
        return switch (amount) {
            case 2900 -> 1;
            case 7900 -> 7;
            case 19900 -> 30;
            default -> throw new IllegalArgumentException("등록되지 않은 이용권 금액입니다.");
        };
    }

    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다.");
        }
    }
}
