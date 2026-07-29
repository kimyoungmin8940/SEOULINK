package com.seoulink.backend.domain.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "PAYMENT", uniqueConstraints = @UniqueConstraint(name = "UK_PAYMENT_ORDER", columnNames = "ORDER_ID"))
@Getter @Setter @NoArgsConstructor
/**
 * 데이터베이스에 저장되는 도메인 엔티티입니다.
 */
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "PAYMENT_ID") private Long paymentId;
    @Column(name = "MEMBER_ID", nullable = false) private Long memberId;
    @Column(name = "PRODUCT_NAME", nullable = false, length = 100) private String productName;
    @Column(name = "AMOUNT", nullable = false) private Integer amount = 0;
    @Column(name = "PAYMENT_METHOD", nullable = false, length = 30) private String paymentMethod;
    @Column(name = "PAYMENT_PROVIDER", length = 30) private String paymentProvider;
    @Column(name = "ORDER_ID", nullable = false, unique = true, length = 100) private String orderId;
    @Column(name = "PAYMENT_KEY", length = 200) private String paymentKey;
    @Column(name = "PAYMENT_STATUS", nullable = false, length = 20) private String paymentStatus = "READY";
    @Column(name = "EXPIRED_AT") private LocalDateTime expiredAt;
    @Column(name = "PAID_AT") private LocalDateTime paidAt;
    @Column(name = "CANCELED_AT") private LocalDateTime canceledAt;
    @Column(name = "FAIL_REASON", length = 500) private String failReason;
    @Column(name = "CREATED_AT", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @PrePersist public void prePersist() { if (amount == null) amount = 0; if (paymentStatus == null) paymentStatus = "READY"; if (createdAt == null) createdAt = LocalDateTime.now(); }
    // 기간권은 사용 횟수를 차감하지 않고 만료 시각까지 사용할 수 있다.
    public void markPaid(String paymentKey, LocalDateTime expiredAt) { this.paymentKey = paymentKey; this.paymentStatus = "PAID"; this.expiredAt = expiredAt; this.paidAt = LocalDateTime.now(); }
    public void markCanceled(String reason) { paymentStatus = "CANCELED"; canceledAt = LocalDateTime.now(); failReason = reason; }
    public void markFailed(String reason) { paymentStatus = "FAILED"; failReason = reason; }
}
