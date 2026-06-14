package com.back.popspot.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	// 해당 예약의 승인 완료된 예약 결제 존재 여부 확인
	boolean existsByReservationIdAndPaymentTypeAndStatus(Long reservationId, PaymentType paymentType, String status);

	// 같은 예약자의 같은 예약 결제 재요청 조회
	Optional<Payment> findByIdempotencyKeyAndReservationIdAndMemberIdAndPaymentType(
		String idempotencyKey,
		Long reservationId,
		Long memberId,
		PaymentType paymentType
	);
}
