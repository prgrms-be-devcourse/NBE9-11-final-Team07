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
	public static final String READY_STATUS = "READY";
	public static final String DONE_STATUS = "DONE";

	public static Payment createReady(
		User member,
		PaymentType paymentType,
		String orderId,
		String orderName,
		long amount,
		String idempotencyKey
	) {
		Payment payment = new Payment();
		payment.member = member;
		payment.paymentType = paymentType;
		payment.orderId = orderId;
		payment.orderName = orderName;
		payment.amount = amount;
		payment.status = READY_STATUS;
		payment.idempotencyKey = idempotencyKey;
		return payment;
	}

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private User member;

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

	@Column(length = 30, nullable = false)
	private String status;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "idempotency_key", length = 255, unique = true, nullable = false)
	private String idempotencyKey;

	private Payment(
		User member,
		Reservation reservation,
		GoodsOrder goodsOrder,
		PaymentType paymentType,
		String orderId,
		String orderName,
		long amount,
		String status,
		String idempotencyKey
	) {
		this.member = member;
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
		User member,
		Reservation reservation,
		String orderId,
		String orderName,
		long amount,
		String idempotencyKey
	) {
		return new Payment(
			member,
			reservation,
			null,
			PaymentType.POPUP,
			orderId,
			orderName,
			amount,
			"READY",
			idempotencyKey
		);
	}

	public boolean isDone() {
		return DONE_STATUS.equals(status);
	}

	public boolean isReady() {
		return READY_STATUS.equals(status);
	}

	public void complete(String paymentKey, LocalDateTime approvedAt) {
		this.paymentKey = paymentKey;
		this.status = DONE_STATUS;
		this.approvedAt = approvedAt;

		if (reservation != null) {
			reservation.confirm();
		}

		if (goodsOrder != null) {
			goodsOrder.updateStatus(GoodsOrderStatus.PAID);
		}
	}
}
