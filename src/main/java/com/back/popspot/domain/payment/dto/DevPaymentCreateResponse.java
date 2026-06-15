package com.back.popspot.domain.payment.dto;

import com.back.popspot.domain.payment.entity.Payment;

public record DevPaymentCreateResponse(
	Long paymentId,
	String orderId,
	String orderName,
	long amount,
	String status
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
