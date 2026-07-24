package com.seoulink.backend.domain.payment.dto.response;

import com.seoulink.backend.domain.payment.entity.Payment;

import java.time.LocalDateTime;

/** 마이페이지 결제 내역 화면에 필요한 결제 정보만 반환한다. */
public record PaymentHistoryResponse(
        Long paymentId,
        String productName,
        Integer amount,
        String paymentMethod,
        String paymentProvider,
        String orderId,
        String paymentStatus,
        LocalDateTime expiredAt,
        LocalDateTime paidAt,
        LocalDateTime canceledAt,
        LocalDateTime createdAt
) {
    public static PaymentHistoryResponse from(Payment payment) {
        return new PaymentHistoryResponse(
                payment.getPaymentId(),
                payment.getProductName(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentProvider(),
                payment.getOrderId(),
                payment.getPaymentStatus(),
                payment.getExpiredAt(),
                payment.getPaidAt(),
                payment.getCanceledAt(),
                payment.getCreatedAt()
        );
    }
}
