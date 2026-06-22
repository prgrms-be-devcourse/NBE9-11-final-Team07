package com.back.popspot.domain.payment.dto;

public record PaymentCancelCommand(
	Long paymentId,
	String paymentKey,
	String orderId,
	long amount,
	String cancelReason,
	String idempotencyKey
) {
}
