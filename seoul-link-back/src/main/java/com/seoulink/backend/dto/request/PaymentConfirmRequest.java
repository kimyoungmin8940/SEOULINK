package com.seoulink.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentConfirmRequest {
    @NotBlank
    @Size(max = 100)
    private String orderId;

    @NotBlank
    @Size(max = 200)
    private String paymentKey;

    @Min(1)
    private Integer remainCount;

    @Min(1)
    private Integer validDays;
}
