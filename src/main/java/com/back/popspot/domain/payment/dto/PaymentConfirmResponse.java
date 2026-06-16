package com.back.popspot.domain.payment.dto;

import java.time.LocalDateTime;

import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;

public record PaymentConfirmResponse(
	Long paymentId,
	PaymentType paymentType,
	String orderId,
	String paymentKey,
	String orderName,
	long amount,
	PaymentStatus status,
	LocalDateTime approvedAt
) {

	public static PaymentConfirmResponse from(Payment payment) {
		return new PaymentConfirmResponse(
			payment.getId(),
			payment.getPaymentType(),
			payment.getOrderId(),
			payment.getPaymentKey(),
			payment.getOrderName(),
			payment.getAmount(),
			payment.getStatus(),
			payment.getApprovedAt()
		);
	}
}
