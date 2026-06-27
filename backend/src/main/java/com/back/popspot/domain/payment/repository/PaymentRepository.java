package com.back.popspot.domain.payment.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	// 해당 예약의 승인 완료된 예약 결제 존재 여부 확인
	boolean existsByReservationIdAndPaymentTypeAndStatus(
		Long reservationId,
		PaymentType paymentType,
		PaymentStatus status
	);

	// 해당 예약의 특정 상태 결제를 조회
	Optional<Payment> findByReservationIdAndPaymentTypeAndStatus(
		Long reservationId,
		PaymentType paymentType,
		PaymentStatus status
	);

	// 같은 멱등성 키로 생성된 기존 결제 조회
	Optional<Payment> findByIdempotencyKey(String idempotencyKey);

	// 주문 번호로 결제를 조회
	Optional<Payment> findByOrderId(String orderId);

	// READY 상태 결제를 승인 진행 상태로 조건부 변경
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update Payment p
		set p.status = :confirmingStatus,
			p.paymentKey = :paymentKey,
			p.confirmIdempotencyKey = :idempotencyKey,
			p.confirmStartedAt = :confirmStartedAt
		where p.orderId = :orderId
			and p.status = :readyStatus
			and p.amount = :amount
		""")
	int beginConfirmationIfReady(
		@Param("orderId") String orderId,
		@Param("amount") long amount,
		@Param("paymentKey") String paymentKey,
		@Param("idempotencyKey") String idempotencyKey,
		@Param("confirmStartedAt") LocalDateTime confirmStartedAt,
		@Param("readyStatus") PaymentStatus readyStatus,
		@Param("confirmingStatus") PaymentStatus confirmingStatus
	);

	// 굿즈 주문에 연결된 결제 조회 (환불 오케스트레이션 시 사용)
	Optional<Payment> findByGoodsOrder_Id(Long goodsOrderId);

}
