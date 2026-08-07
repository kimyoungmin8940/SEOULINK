package com.seoulink.backend.domain.payment.service;

import com.seoulink.backend.domain.payment.dto.request.PaymentConfirmRequest;
import com.seoulink.backend.domain.payment.dto.request.PaymentCreateRequest;
import com.seoulink.backend.domain.payment.dto.response.TossPaymentResponse;
import com.seoulink.backend.infrastructure.external.toss.TossPaymentsClient;
import com.seoulink.backend.domain.payment.entity.Payment;
import com.seoulink.backend.domain.member.repository.MemberRepository;
import com.seoulink.backend.domain.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 주문의 상태 전이를 관리한다. 주문 생성 시 서버 기준 금액을 기록하고,
 * 승인 시에는 결제사 응답과 저장된 주문 금액을 대조한 뒤 이용권을 활성화한다.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final TossPaymentsClient tossPaymentsClient;

    /**
     * 서버가 금액을 기준으로 확정하는 이용권 정보입니다.
     * 클라이언트가 보낸 상품명은 표시 정보로 신뢰하지 않습니다.
     */
    private record PlanInfo(String productName, int durationDays) {}


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
    // 금액과 회원을 검증한 뒤 결제 대기 주문을 저장한다.
    public Payment readyPayment(PaymentCreateRequest request) {
        validateMemberExists(request.getMemberId());

        PlanInfo plan = resolvePlan(request.getAmount());

        boolean alreadyHasActivePass =
                paymentRepository
                        .existsByMemberIdAndProductNameAndPaymentStatusAndExpiredAtAfter(
                                request.getMemberId(),
                                plan.productName(),
                                "PAID",
                                LocalDateTime.now()
                        );

        if (alreadyHasActivePass) {
            throw new IllegalStateException(
                    "이미 사용 중인 동일 이용권이 있습니다. 만료 후 다시 구매할 수 있습니다."
            );
        }

        if (!"TOSS".equals(request.getPaymentProvider())) {
            throw new IllegalArgumentException(
                    "지원하지 않는 결제 제공사입니다."
            );
        }

        // 아래 기존 결제 주문 생성 코드는 그대로 유지


        Payment payment = new Payment();
        payment.setMemberId(request.getMemberId());

        // 상품명은 요청값이 아니라 서버의 이용권 정책으로 결정한다.
        payment.setProductName(plan.productName());
        payment.setAmount(request.getAmount());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setPaymentProvider("TOSS");
        payment.setOrderId(request.getOrderId());
        payment.setPaymentStatus("READY");

        return paymentRepository.save(payment);
    }

    /**
     * 토스 결제 인증 완료 후 서버에서 최종 승인을 처리합니다.
     */
    @Transactional
    // 토스 응답과 서버 주문의 주문번호·금액·통화를 대조한 뒤 결제를 완료 처리한다.
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

        PlanInfo plan = resolvePlan(order.getAmount());

        order.markPaid(
                request.getPaymentKey(),
                LocalDateTime.now().plusDays(plan.durationDays())
        );

        return order;
    }

    /**
     * 기존 호출부와의 호환을 위한 메서드입니다.
     */
    @Transactional
    // 이전 호출부와의 호환을 위해 READY 주문 생성 로직으로 위임한다.
    public Payment createPayment(PaymentCreateRequest request) {
        return readyPayment(request);
    }

    /**
     * 회원의 결제 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    // 결제 이력 조회 전 회원 존재 여부를 확인해 잘못된 조회를 방지한다.
    public List<Payment> getPayments(Long memberId) {
        validateMemberExists(memberId);

        return paymentRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    /**
     * 토스 환불을 수행하지 않고, 현재 회원의 프로젝트 내 결제 기록만 삭제한다.
     */
    @Transactional
    // 환불 API를 호출하지 않고, 본인의 서비스 내 결제 이력만 삭제한다.
    public void deletePayment(Long paymentId, Long memberId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        if (!payment.getMemberId().equals(memberId)) {
            throw new IllegalArgumentException("본인의 결제내역만 삭제할 수 있습니다.");
        }

        paymentRepository.delete(payment);
    }

    /**
     * 결제를 취소 상태로 변경합니다.
     *
     * 현재는 로컬 상태만 변경합니다.
     * 실제 토스 결제 취소 API 연동은 별도로 구현해야 합니다.
     */
    @Transactional
    // 현재 구현은 외부 환불 대신 내부 결제 상태만 취소로 변경한다.
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
     * 결제사 실패 URL에서 호출한다. 아직 승인되지 않은 READY 주문만 변경하므로,
     * 이미 완료된 결제를 실패 콜백으로 덮어쓰지 않는다.
     */
    @Transactional
    public Payment failPayment(String orderId, String reason, boolean canceled) {
        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 주문을 찾을 수 없습니다."));

        if (!"READY".equals(payment.getPaymentStatus())) {
            return payment;
        }

        if (canceled) {
            payment.markCanceled(reason);
        } else {
            payment.markFailed(reason);
        }

        return payment;
    }

    /**
     * 토스 승인 응답과 서버 주문 정보를 검증합니다.
     */
    // 외부 결제 승인 결과가 서버가 생성한 주문과 일치하는지 검증한다.
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
    // 허용된 이용권 금액만 승인하고, 선택한 이용 기간을 반환한다.
    /**
     * 결제 금액에 해당하는 이용권을 서버 정책으로 결정합니다.
     *
     * <p>프론트에서 전달한 상품명은 조작될 수 있으므로 저장하지 않습니다.
     * 이 메서드가 상품명과 이용 기간의 단일 기준이 됩니다.</p>
     */
    private PlanInfo resolvePlan(int amount) {
        return switch (amount) {
            case 9_900 -> new PlanInfo("하루 패스", 1);
            case 29_900 -> new PlanInfo("위클리 패스", 7);
            case 69_900 -> new PlanInfo("트래블 패스", 30);
            default -> throw new IllegalArgumentException(
                    "등록되지 않은 이용권 금액입니다."
            );
        };
    }
    /**
     * 회원 존재 여부를 확인합니다.
     */
    // 결제 레코드가 존재하지 않는 회원과 연결되지 않도록 검증한다.
    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException(
                    "회원 정보를 찾을 수 없습니다."
            );
        }
    }
}
