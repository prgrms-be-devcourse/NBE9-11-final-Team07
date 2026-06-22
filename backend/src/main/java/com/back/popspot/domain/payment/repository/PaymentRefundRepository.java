package com.back.popspot.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.payment.entity.PaymentRefund;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
	Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

	Optional<PaymentRefund> findFirstByPaymentIdOrderByIdDesc(Long paymentId);
}
