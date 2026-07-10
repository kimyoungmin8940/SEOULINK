package com.seoulink.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "PAYMENT",
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_PAYMENT_ORDER", columnNames = "ORDER_ID")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PAYMENT_ID")
    private Long paymentId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "PRODUCT_NAME", nullable = false, length = 100)
    private String productName;

    @Column(name = "AMOUNT", nullable = false)
    private Integer amount = 0;

    @Column(name = "PAYMENT_METHOD", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "PAYMENT_PROVIDER", length = 30)
    private String paymentProvider;

    @Column(name = "ORDER_ID", nullable = false, unique = true, length = 100)
    private String orderId;

    @Column(name = "PAYMENT_KEY", length = 200)
    private String paymentKey;

    @Column(name = "PAYMENT_STATUS", nullable = false, length = 20)
    private String paymentStatus = "READY";

    @Column(name = "REMAIN_COUNT", nullable = false)
    private Integer remainCount = 0;

    @Column(name = "EXPIRED_AT")
    private LocalDateTime expiredAt;

    @Column(name = "PAID_AT")
    private LocalDateTime paidAt;

    @Column(name = "CANCELED_AT")
    private LocalDateTime canceledAt;

    @Column(name = "FAIL_REASON", length = 500)
    private String failReason;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (amount == null) amount = 0;
        if (paymentStatus == null) paymentStatus = "READY";
        if (remainCount == null) remainCount = 0;
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public void markPaid(String paymentKey, int remainCount, LocalDateTime expiredAt) {
        this.paymentKey = paymentKey;
        this.paymentStatus = "PAID";
        this.remainCount = remainCount;
        this.expiredAt = expiredAt;
        this.paidAt = LocalDateTime.now();
    }

    public void markCanceled(String reason) {
        this.paymentStatus = "CANCELED";
        this.canceledAt = LocalDateTime.now();
        this.failReason = reason;
    }

    public void markFailed(String reason) {
        this.paymentStatus = "FAILED";
        this.failReason = reason;
    }

    public void useOneCount() {
        // 포트원 기간권은 만료일까지 횟수 제한 없이 사용한다.
        if ("PORTONE".equals(this.paymentProvider) || "DEMO".equals(this.paymentProvider)) {
            return;
        }
        if (this.remainCount == null || this.remainCount <= 0) {
            throw new IllegalStateException("남은 챗봇 이용권이 없습니다.");
        }
        this.remainCount -= 1;
    }
}
