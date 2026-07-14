package com.seoulink.backend.dto.response;

public record TossPaymentResponse(
        String status,
        String orderId,
        Integer totalAmount,
        String currency
) {
}