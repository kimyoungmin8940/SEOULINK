package com.seoulink.backend.domain.payment.dto.request;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class PaymentConfirmRequest { @NotBlank @Size(max = 200) private String paymentKey; @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9_\\-]+$") private String orderId; @NotNull @Min(1) private Integer amount; }
