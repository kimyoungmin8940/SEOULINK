package com.seoulink.backend.domain.payment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * 토스 결제 승인에 필요한 결제키, 주문번호, 금액을 전달하고 검증하는 DTO입니다.
 */
public class PaymentConfirmRequest {

    /** 토스가 발급한 결제 승인 식별자입니다. */
    @NotBlank
    @Size(max = 200)
    private String paymentKey;

    /** 서버에서 READY 상태로 생성한 주문번호입니다. */
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[A-Za-z0-9_\\-]+$")
    private String orderId;

    /** 서버 주문 금액과 대조할 결제 승인 금액입니다. */
    @NotNull
    @Min(1)
    private Integer amount;
}
