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

    @PostMapping("/ready")
    public Payment readyPayment(@Valid @RequestBody PaymentCreateRequest request) {
        return paymentService.readyPayment(request);
    }

    @PostMapping("/complete")
    public Payment confirmPayment(@Valid @RequestBody PaymentConfirmRequest request) {
        return paymentService.confirmPayment(request);
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
