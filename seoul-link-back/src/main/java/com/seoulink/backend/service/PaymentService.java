package com.seoulink.backend.service;

import com.seoulink.backend.dto.request.PaymentConfirmRequest;
import com.seoulink.backend.dto.request.PaymentCreateRequest;
import com.seoulink.backend.dto.response.TossPaymentResponse;
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
    private final TossPaymentsClient tossPaymentsClient;

    public PaymentService(
            PaymentRepository paymentRepository,
            MemberRepository memberRepository,
            TossPaymentsClient tossPaymentsClient
    ) {
        this.paymentRepository = paymentRepository;
        this.memberRepository = memberRepository;
        this.tossPaymentsClient = tossPaymentsClient;
    }

    /**
     * 토스 결제를 요청하기 전에 주문 정보를 READY 상태로 저장합니다.
     */
    @Transactional
    public Payment readyPayment(PaymentCreateRequest request) {
        validateMemberExists(request.getMemberId());
        validatePlan(request.getAmount());

        if (!"TOSS".equals(request.getPaymentProvider())) {
            throw new IllegalArgumentException(
                    "지원하지 않는 결제 제공자입니다."
            );
        }

        Payment payment = new Payment();
        payment.setMemberId(request.getMemberId());
        payment.setProductName(request.getProductName());
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentProvider("TOSS");
        payment.setOrderId(request.getOrderId());
        payment.setPaymentStatus("READY");
        payment.setRemainCount(1);

        return paymentRepository.save(payment);
    }

    /**
     * 토스 결제 인증 완료 후 서버에서 최종 승인을 처리합니다.
     */
    @Transactional
    public Payment confirmPayment(PaymentConfirmRequest request) {
        Payment order = paymentRepository
                .findByOrderId(request.getOrderId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "결제 주문을 찾을 수 없습니다."
                        )
                );

        if (!"READY".equals(order.getPaymentStatus())) {
            if ("PAID".equals(order.getPaymentStatus())) {
                return order;
            }

            throw new IllegalStateException(
                    "승인할 수 없는 결제 상태입니다."
            );
        }

        if (!order.getAmount().equals(request.getAmount())) {
            throw new IllegalArgumentException(
                    "주문 금액이 일치하지 않습니다."
            );
        }

        TossPaymentResponse tossPayment =
                tossPaymentsClient.confirm(
                        request.getPaymentKey(),
                        request.getOrderId(),
                        request.getAmount()
                );

        validateTossPayment(order, tossPayment);

        int durationDays = validatePlan(order.getAmount());

        order.markPaid(
                request.getPaymentKey(),
                1,
                LocalDateTime.now().plusDays(durationDays)
        );

        return order;
    }

    /**
     * 기존 호출부와의 호환을 위한 메서드입니다.
     */
    @Transactional
    public Payment createPayment(PaymentCreateRequest request) {
        return readyPayment(request);
    }

    /**
     * 회원의 결제 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<Payment> getPayments(Long memberId) {
        validateMemberExists(memberId);

        return paymentRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    /**
     * 결제를 취소 상태로 변경합니다.
     *
     * 현재는 로컬 상태만 변경합니다.
     * 실제 토스 결제 취소 API 연동은 별도로 구현해야 합니다.
     */
    @Transactional
    public Payment cancelPayment(
            Long paymentId,
            String reason
    ) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "결제 정보를 찾을 수 없습니다."
                        )
                );

        payment.markCanceled(reason);

        return payment;
    }

    /**
     * 토스 승인 응답과 서버 주문 정보를 검증합니다.
     */
    private void validateTossPayment(
            Payment order,
            TossPaymentResponse tossPayment
    ) {
        if (tossPayment == null) {
            throw new IllegalStateException(
                    "토스 결제 승인 응답이 없습니다."
            );
        }

        if (!"DONE".equals(tossPayment.status())) {
            throw new IllegalStateException(
                    "토스 결제가 완료되지 않았습니다."
            );
        }

        if (!order.getOrderId().equals(tossPayment.orderId())) {
            throw new IllegalStateException(
                    "주문번호가 일치하지 않습니다."
            );
        }

        if (!order.getAmount().equals(tossPayment.totalAmount())) {
            throw new IllegalStateException(
                    "결제 금액이 일치하지 않습니다."
            );
        }

        if (!"KRW".equals(tossPayment.currency())) {
            throw new IllegalStateException(
                    "결제 통화가 일치하지 않습니다."
            );
        }
    }

    /**
     * 등록된 이용권 가격인지 검증하고 사용 기간을 반환합니다.
     */
    private int validatePlan(int amount) {
        return switch (amount) {
            case 9_900 -> 1;
            case 29_900 -> 7;
            case 69_900 -> 30;
            default -> throw new IllegalArgumentException(
                    "등록되지 않은 이용권 금액입니다."
            );
        };
    }

    /**
     * 회원 존재 여부를 확인합니다.
     */
    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException(
                    "회원 정보를 찾을 수 없습니다."
            );
        }
    }
}