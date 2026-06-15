package com.back.popspot.domain.payment.dto;

// 승인 요청 데이터를 담음
public record PaymentConfirmRequest(
	String paymentKey,
	String orderId,
	long amount
) {
}
