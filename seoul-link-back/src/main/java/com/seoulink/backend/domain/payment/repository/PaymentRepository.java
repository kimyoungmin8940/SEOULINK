package com.seoulink.backend.domain.payment.repository;

import com.seoulink.backend.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    Optional<Payment> findByOrderId(String orderId);
    Optional<Payment> findFirstByMemberIdAndPaymentStatusAndRemainCountGreaterThanOrderByPaidAtDesc(
            Long memberId,
            String paymentStatus,
            Integer remainCount
    );
}
