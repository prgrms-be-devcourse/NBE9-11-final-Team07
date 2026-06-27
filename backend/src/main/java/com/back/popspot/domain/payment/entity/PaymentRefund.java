package com.back.popspot.domain.payment.entity;

import java.time.LocalDateTime;

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
@Table(name = "payment_refund")
public class PaymentRefund extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_id", nullable = false)
	private Payment payment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "refund_amount", nullable = false)
	private long refundAmount;

	@Enumerated(EnumType.STRING)
	@Column(length = 30, nullable = false)
	private PaymentRefundStatus status;

	@Column(length = 255)
	private String reason;

	@Column(name = "idempotency_key", length = 300, unique = true, nullable = false)
	private String idempotencyKey;

	@Column(name = "transaction_key", length = 64)
	private String transactionKey;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	// 환불 요청 엔티티를 생성
	public static PaymentRefund request(
		Payment payment,
		User user,
		long refundAmount,
		String reason,
		String idempotencyKey
	) {
		PaymentRefund refund = new PaymentRefund();
		refund.payment = payment;
		refund.user = user;
		refund.refundAmount = refundAmount;
		refund.status = PaymentRefundStatus.REQUESTED;
		refund.reason = reason;
		refund.idempotencyKey = idempotencyKey;
		return refund;
	}

	// 환불 요청 상태로 재시도 처리
	public void retry() {
		this.status = PaymentRefundStatus.REQUESTED;
	}

	// 환불 완료 상태로 변경
	public void complete(String transactionKey, LocalDateTime completedAt) {
		this.status = PaymentRefundStatus.DONE;
		this.transactionKey = transactionKey;
		this.completedAt = completedAt;
	}

	// 환불 실패 상태로 변경
	public void fail() {
		this.status = PaymentRefundStatus.FAILED;
	}
}
