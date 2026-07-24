package com.seoulink.backend.domain.payment.repository;

import com.seoulink.backend.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 도메인 데이터를 조회하고 저장하는 리포지토리입니다.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    Optional<Payment> findByOrderId(String orderId);
    // 가장 최근에 결제됐고 아직 만료되지 않은 기간권을 찾는다.
    Optional<Payment> findFirstByMemberIdAndPaymentStatusAndExpiredAtAfterOrderByPaidAtDesc(
            Long memberId,
            String paymentStatus,
            java.time.LocalDateTime now
    );
}
