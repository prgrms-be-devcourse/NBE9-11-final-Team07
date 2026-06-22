package com.back.popspot.domain.payment.dto;

public record PaymentCancelPreparation(
	PaymentCancelCommand command,
	PaymentCancelResponse existingResponse
) {
	public static PaymentCancelPreparation required(PaymentCancelCommand command) {
		return new PaymentCancelPreparation(command, null);
	}

	public static PaymentCancelPreparation completed(PaymentCancelResponse response) {
		return new PaymentCancelPreparation(null, response);
	}

	public boolean isCompleted() {
		return existingResponse != null;
	}
}
