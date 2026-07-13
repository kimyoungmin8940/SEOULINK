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
    private final PaymentRepository paymentRepository; private final MemberRepository memberRepository; private final TossPaymentsClient tossPaymentsClient;
    public PaymentService(PaymentRepository paymentRepository, MemberRepository memberRepository, TossPaymentsClient tossPaymentsClient) { this.paymentRepository = paymentRepository; this.memberRepository = memberRepository; this.tossPaymentsClient = tossPaymentsClient; }
    @Transactional public Payment readyPayment(PaymentCreateRequest request) {
        validateMemberExists(request.getMemberId()); validatePlan(request.getAmount());
        if (!"TOSS".equals(request.getPaymentProvider())) throw new IllegalArgumentException("지원하지 않는 결제수단입니다.");
        Payment payment = new Payment(); payment.setMemberId(request.getMemberId()); payment.setProductName(request.getProductName()); payment.setAmount(request.getAmount()); payment.setPaymentMethod(request.getPaymentMethod()); payment.setPaymentProvider("TOSS"); payment.setOrderId(request.getOrderId()); payment.setPaymentStatus("READY"); payment.setRemainCount(1); return paymentRepository.save(payment);
    }
    @Transactional public Payment confirmPayment(PaymentConfirmRequest request) {
        Payment order = paymentRepository.findByOrderId(request.getOrderId()).orElseThrow(() -> new IllegalArgumentException("결제 주문을 찾을 수 없습니다."));
        if (!"READY".equals(order.getPaymentStatus())) { if ("PAID".equals(order.getPaymentStatus())) return order; throw new IllegalStateException("승인할 수 없는 결제 상태입니다."); }
        if (order.getAmount() != request.getAmount()) throw new IllegalArgumentException("주문 금액이 일치하지 않습니다.");
        JsonNode payment = tossPaymentsClient.confirm(request.getPaymentKey(), request.getOrderId(), request.getAmount()); validateTossPayment(order, payment); order.markPaid(request.getPaymentKey(), 1, LocalDateTime.now().plusDays(validatePlan(order.getAmount()))); return order;
    }
    @Transactional public Payment createPayment(PaymentCreateRequest request) { return readyPayment(request); }
    public List<Payment> getPayments(Long memberId) { validateMemberExists(memberId); return paymentRepository.findByMemberIdOrderByCreatedAtDesc(memberId); }
    @Transactional public Payment cancelPayment(Long paymentId, String reason) { Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다.")); payment.markCanceled(reason); return payment; }
    private void validateTossPayment(Payment order, JsonNode payment) { if (!"DONE".equals(payment.path("status").asText())) throw new IllegalStateException("토스 결제가 완료되지 않았습니다."); if (!order.getOrderId().equals(payment.path("orderId").asText())) throw new IllegalStateException("주문번호가 일치하지 않습니다."); if (order.getAmount() != payment.path("totalAmount").asInt()) throw new IllegalStateException("결제 금액이 일치하지 않습니다."); if (!"KRW".equals(payment.path("currency").asText())) throw new IllegalStateException("결제 통화가 일치하지 않습니다."); }
    private int validatePlan(int amount) { return switch (amount) { case 9900 -> 1; case 29900 -> 7; case 69900 -> 30; default -> throw new IllegalArgumentException("등록되지 않은 이용권 금액입니다."); }; }
    private void validateMemberExists(Long memberId) { if (!memberRepository.existsById(memberId)) throw new IllegalArgumentException("회원 정보를 찾을 수 없습니다."); }
}
