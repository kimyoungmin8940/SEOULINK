package com.seoulink.backend.controller;

import com.seoulink.backend.dto.request.PaymentConfirmRequest;
import com.seoulink.backend.dto.request.PaymentCreateRequest;
import com.seoulink.backend.entity.Payment;
import com.seoulink.backend.service.PaymentService;
import jakarta.validation.Valid;
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
    public List<Payment> getPayments(@RequestParam Long memberId) {
        return paymentService.getPayments(memberId);
    }

    @PatchMapping("/{paymentId}/cancel")
    public Payment cancelPayment(
            @PathVariable Long paymentId,
            @RequestParam(defaultValue = "사용자 요청") String reason
    ) {
        return paymentService.cancelPayment(paymentId, reason);
    }
}
