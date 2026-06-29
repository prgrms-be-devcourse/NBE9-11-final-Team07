package com.back.popspot.domain.payment.entity;

import java.time.LocalDateTime;

import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "payment")
public class Payment extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// payment_type = POPUP 일 때 사용
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reservation_id")
	private Reservation reservation;

	// payment_type = GOODS 일 때 사용
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "goods_order_id")
	private GoodsOrder goodsOrder;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_type", nullable = false)
	private PaymentType paymentType;

	@Column(name = "order_id", length = 100, unique = true, nullable = false)
	private String orderId;

	@Column(name = "payment_key", length = 255, unique = true)
	private String paymentKey;

	@Column(name = "order_name", length = 255, nullable = false)
	private String orderName;

	@Column(nullable = false)
	private long amount;

	@Enumerated(EnumType.STRING)
	@Column(length = 30, nullable = false)
	private PaymentStatus status;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "idempotency_key", length = 255, unique = true, nullable = false)
	private String idempotencyKey;

	// confirm 상태 확인용 멱등성 키
	@Column(name = "confirm_idempotency_key", length = 255)
	private String confirmIdempotencyKey;

	// confirm 상태 변경 시간
	@Column(name = "confirm_started_at")
	private LocalDateTime confirmStartedAt;

	// 결제 엔티티를 초기화
	private Payment(
		User user,
		Reservation reservation,
		GoodsOrder goodsOrder,
		PaymentType paymentType,
		String orderId,
		String orderName,
		long amount,
		PaymentStatus status,
		String idempotencyKey
	) {
		this.user = user;
		this.reservation = reservation;
		this.goodsOrder = goodsOrder;
		this.paymentType = paymentType;
		this.orderId = orderId;
		this.orderName = orderName;
		this.amount = amount;
		this.status = status;
		this.idempotencyKey = idempotencyKey;
	}

	// 대상을 특정하지 않은 READY 결제를 생성
	public static Payment createReady(
		User user,
		PaymentType paymentType,
		String orderId,
		String orderName,
		long amount,
		String idempotencyKey
	) {
		Payment payment = new Payment();
		payment.user = user;
		payment.paymentType = paymentType;
		payment.orderId = orderId;
		payment.orderName = orderName;
		payment.amount = amount;
		payment.status = PaymentStatus.READY;
		payment.idempotencyKey = idempotencyKey;
		return payment;
	}

	// 예약용 READY 결제를 생성
	public static Payment createReadyReservationPayment(
		User user,
		Reservation reservation,
		String orderId,
		String orderName,
		long amount,
		String idempotencyKey
	) {
		return new Payment(
			user,
			reservation,
			null,
			PaymentType.POPUP,
			orderId,
			orderName,
			amount,
			PaymentStatus.READY,
			idempotencyKey
		);
	}

	// 굿즈 주문용 READY 결제를 생성
	public static Payment createReadyGoodsOrderPayment(
		User user,
		GoodsOrder goodsOrder,
		String orderId,
		String orderName,
		long amount,
		String idempotencyKey
	) {
		return new Payment(
			user,
			null,
			goodsOrder,
			PaymentType.GOODS,
			orderId,
			orderName,
			amount,
			PaymentStatus.READY,
			idempotencyKey
		);
	}

	// 결제 완료 상태인지 확인
	public boolean isPaid() {
		return status == PaymentStatus.PAID;
	}

	// 결제 준비 상태인지 확인
	public boolean isReady() {
		return status == PaymentStatus.READY;
	}

	// 결제 승인 진행 중 상태인지 확인
	public boolean isConfirming() {
		return status == PaymentStatus.CONFIRMING;
	}

	// 결제 취소 완료 상태인지 확인
	public boolean isCanceled() {
		return status == PaymentStatus.CANCELED;
	}

	// 결제 취소 진행 중 상태인지 확인
	public boolean isCanceling() {
		return status == PaymentStatus.CANCELING;
	}

	// 결제 취소 실패 상태인지 확인
	public boolean isCancelFailed() {
		return status == PaymentStatus.CANCEL_FAILED;
	}

	// 결제 승인 진행 상태로 변경
	public void beginConfirmation() {
		this.status = PaymentStatus.CONFIRMING;
	}

	// 결제를 승인 완료 처리
	public void complete(String paymentKey, LocalDateTime approvedAt) {
		this.paymentKey = paymentKey;
		this.status = PaymentStatus.PAID;
		this.approvedAt = approvedAt;

		if (reservation != null) {
			reservation.confirm();
		}

		if (goodsOrder != null) {
			goodsOrder.updateStatus(GoodsOrderStatus.PAID);
		}
	}

	// 결제를 취소 완료 처리
	public void cancel() {
		this.status = PaymentStatus.CANCELED;
	}

	// 결제 취소 진행 상태로 변경
	public void beginCancel() {
		this.status = PaymentStatus.CANCELING;
	}

	// 결제 취소 실패 상태로 변경
	public void failCancel() {
		this.status = PaymentStatus.CANCEL_FAILED;
	}

	// 결제 취소 재시도 상태로 변경
	public void retryCancel() {
		this.status = PaymentStatus.CANCELING;
	}

	// 보상 취소 진행 상태로 변경
	public void beginCompensation(String paymentKey) {
		this.paymentKey = paymentKey;
		this.status = PaymentStatus.COMPENSATING;
	}

	// 보상 취소 실패 상태로 변경
	public void failCompensation() {
		this.status = PaymentStatus.COMPENSATION_FAILED;
	}

	// 보상 취소 재시도 상태로 변경
	public void retryCompensation() {
		this.status = PaymentStatus.COMPENSATING;
	}

	// 이미 존재하는 결제인지 확인
	public boolean hasSameConfirmRequest(String paymentKey, String idempotencyKey) {
		return paymentKey.equals(this.paymentKey)
			&& idempotencyKey.equals(this.confirmIdempotencyKey);
	}

	// 기존 결제 키와 다른 결제 키인지 확인
	public boolean hasDifferentPaymentKey(String paymentKey) {
		return this.paymentKey != null && !this.paymentKey.equals(paymentKey);
	}

	// 결제 승인 진행 상태가 만료되었는지 확인
	public boolean isConfirmStale(LocalDateTime now, long stateSeconds) {
		return confirmStartedAt != null && confirmStartedAt.plusSeconds(stateSeconds).isBefore(now);
	}
}
