package com.back.popspot.domain.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.back.popspot.domain.payment.entity.PaymentRefund;
import com.back.popspot.domain.payment.entity.PaymentRefundStatus;

import jakarta.persistence.LockModeType;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<PaymentRefund> findTop20ByStatusOrderByIdAsc(PaymentRefundStatus status);
	Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

	Optional<PaymentRefund> findFirstByPaymentIdOrderByIdDesc(Long paymentId);
}
