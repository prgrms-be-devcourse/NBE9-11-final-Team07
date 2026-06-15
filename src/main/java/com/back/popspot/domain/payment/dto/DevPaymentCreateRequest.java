package com.back.popspot.domain.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DevPaymentCreateRequest(
	@NotBlank @Size(max = 255) String orderName,
	@Min(1) long amount
) {
}
