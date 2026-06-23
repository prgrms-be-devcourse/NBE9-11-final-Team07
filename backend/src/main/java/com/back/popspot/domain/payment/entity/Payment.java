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

	public boolean isPaid() {
		return status == PaymentStatus.PAID;
	}

	public boolean isReady() {
		return status == PaymentStatus.READY;
	}

	public boolean isConfirming() {
		return status == PaymentStatus.CONFIRMING;
	}

	public boolean isCanceled() {
		return status == PaymentStatus.CANCELED;
	}

	public void beginConfirmation() {
		this.status = PaymentStatus.CONFIRMING;
	}

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

	public void cancel() {
		this.status = PaymentStatus.CANCELED;
	}

	public void beginCompensation(String paymentKey) {
		this.paymentKey = paymentKey;
		this.status = PaymentStatus.COMPENSATING;
	}

	public void failCompensation() {
		this.status = PaymentStatus.COMPENSATION_FAILED;
	}

	public void retryCompensation() {
		this.status = PaymentStatus.COMPENSATING;
	}
}
