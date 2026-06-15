package com.back.popspot.domain.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

// 승인 요청 데이터를 담음
public record PaymentConfirmRequest(
	@NotBlank String paymentKey,
	@NotBlank String orderId,
	@Min(1) long amount
) {
}
