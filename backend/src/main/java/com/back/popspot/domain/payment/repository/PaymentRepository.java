package com.back.popspot.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	// 해당 예약의 승인 완료된 예약 결제 존재 여부 확인
	boolean existsByReservationIdAndPaymentTypeAndStatus(
		Long reservationId,
		PaymentType paymentType,
		PaymentStatus status
	);

	// 같은 멱등성 키로 생성된 기존 결제 조회
	Optional<Payment> findByIdempotencyKey(String idempotencyKey);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Payment> findByOrderId(String orderId);

	// 굿즈 주문에 연결된 결제 조회 (환불 오케스트레이션 시 사용)
	Optional<Payment> findByGoodsOrder_Id(Long goodsOrderId);
}
