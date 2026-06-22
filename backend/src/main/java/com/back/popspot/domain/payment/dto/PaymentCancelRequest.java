package com.back.popspot.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCancelRequest(
	@NotBlank @Size(max = 200) String cancelReason,
	@NotBlank @Size(max = 300) String idempotencyKey
) {
}
