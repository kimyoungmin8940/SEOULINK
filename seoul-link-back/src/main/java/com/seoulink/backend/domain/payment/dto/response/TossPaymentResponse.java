package com.seoulink.backend.domain.payment.dto.response;

public record TossPaymentResponse(
        String status,
        String orderId,
        Integer totalAmount,
        String currency
) {
}