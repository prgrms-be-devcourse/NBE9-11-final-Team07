package com.back.popspot.domain.payment.dto;

import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;

public record DevPaymentCreateResponse(
	Long paymentId,
	String orderId,
	String orderName,
	long amount,
	PaymentStatus status
) {
	public static DevPaymentCreateResponse from(Payment payment) {
		return new DevPaymentCreateResponse(
			payment.getId(),
			payment.getOrderId(),
			payment.getOrderName(),
			payment.getAmount(),
			payment.getStatus()
		);
	}
}
