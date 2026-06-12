package com.back.popspot.domain.payment.entity;

import java.time.LocalDateTime;

import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
	@JoinColumn(name = "member_id", nullable = false)
	private User member;

	@Column(name = "refund_amount", nullable = false)
	private int refundAmount;

	@Column(length = 30, nullable = false)
	private String status;

	@Column(length = 255)
	private String reason;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;
}
