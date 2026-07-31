package com.seoulink.backend.domain.payment.controller;

import com.seoulink.backend.domain.payment.dto.request.PaymentConfirmRequest;
import com.seoulink.backend.domain.payment.dto.request.PaymentCreateRequest;
import com.seoulink.backend.domain.payment.dto.response.PaymentHistoryResponse;
import com.seoulink.backend.domain.payment.entity.Payment;
import com.seoulink.backend.domain.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 이용권 주문 생성, 외부 결제 승인, 결제 내역 조회를 제공한다.
 * 승인 요청은 클라이언트 콜백에서 오지만, 실제 금액 검증은 서비스에서 수행한다.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public Payment createPayment(@Valid @RequestBody PaymentCreateRequest request) {
        return paymentService.createPayment(request);
    }

    // 외부 결제창을 열기 전에 서버에 READY 상태의 주문을 생성한다.
    @PostMapping("/ready")
    public Payment readyPayment(@Valid @RequestBody PaymentCreateRequest request) {
        return paymentService.readyPayment(request);
    }

    // 결제 성공 후 토스 승인 결과를 서버에서 다시 검증한다.
    @PostMapping("/complete")
    public Payment confirmPayment(@Valid @RequestBody PaymentConfirmRequest request) {
        return paymentService.confirmPayment(request);
    }

    // 결제창 취소 또는 승인 실패 시 READY 주문이 계속 남지 않도록 최종 상태를 기록한다.
    @PatchMapping("/fail")
    public Payment failPayment(
            @RequestParam String orderId,
            @RequestParam(defaultValue = "결제 승인에 실패했습니다.") String reason,
            @RequestParam(defaultValue = "false") boolean canceled
    ) {
        return paymentService.failPayment(orderId, reason, canceled);
    }

    @GetMapping
    public List<PaymentHistoryResponse> getPayments(@RequestParam Long memberId) {
        return paymentService.getPayments(memberId)
                .stream()
                .map(PaymentHistoryResponse::from)
                .toList();
    }

    /** 토스 환불이 아닌, 프로젝트에 저장된 결제내역을 삭제한다. */
    @DeleteMapping("/{paymentId}")
    public ResponseEntity<Void> deletePayment(
            @PathVariable Long paymentId,
            @RequestParam Long memberId
    ) {
        paymentService.deletePayment(paymentId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{paymentId}/cancel")
    public Payment cancelPayment(
            @PathVariable Long paymentId,
            @RequestParam(defaultValue = "사용자 요청") String reason
    ) {
        return paymentService.cancelPayment(paymentId, reason);
    }
}
