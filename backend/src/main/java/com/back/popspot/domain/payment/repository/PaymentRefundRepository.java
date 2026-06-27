package com.back.popspot.domain.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.back.popspot.domain.payment.entity.PaymentRefund;
import com.back.popspot.domain.payment.entity.PaymentRefundStatus;

import jakarta.persistence.LockModeType;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
	// 특정 상태의 환불 재처리 대상을 조회
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<PaymentRefund> findTop20ByStatusOrderByIdAsc(PaymentRefundStatus status);

	// 멱등성 키로 환불 요청을 조회
	Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

	// 결제에 연결된 마지막 환불 요청을 조회
	Optional<PaymentRefund> findFirstByPaymentIdOrderByIdDesc(Long paymentId);
}
