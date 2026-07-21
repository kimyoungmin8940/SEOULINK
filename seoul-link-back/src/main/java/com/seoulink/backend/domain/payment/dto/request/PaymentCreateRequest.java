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
public class PaymentCreateRequest {
    @NotNull
    private Long memberId;

    @NotBlank
    @Size(max = 100)
    private String productName;

    @NotNull
    @Min(0)
    private Integer amount;

    @NotBlank
    @Size(max = 30)
    private String paymentMethod;

    @Size(max = 30)
    private String paymentProvider;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[A-Za-z0-9_\\-]+$")
    private String orderId;

    @Size(max = 200)
    private String paymentKey;
}
