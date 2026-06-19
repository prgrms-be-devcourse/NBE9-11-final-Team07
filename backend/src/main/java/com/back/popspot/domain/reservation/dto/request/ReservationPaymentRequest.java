package com.back.popspot.domain.reservation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReservationPaymentRequest(
	@NotBlank
	@Size(max = 50)
	String name,
	@NotBlank
	@Pattern(regexp = "^010-\\d{4}-\\d{4}$")
	String phone,
	@NotBlank
	String idempotencyKey
) {
}
