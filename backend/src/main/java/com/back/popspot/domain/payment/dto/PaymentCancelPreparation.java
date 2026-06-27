package com.back.popspot.domain.payment.dto;

public record PaymentCancelPreparation(
	PaymentCancelCommand command,
	PaymentCancelResponse existingResponse
) {
	// 토스 취소 요청이 필요한 준비 결과를 생성
	public static PaymentCancelPreparation required(PaymentCancelCommand command) {
		return new PaymentCancelPreparation(command, null);
	}

	// 이미 완료된 취소 응답을 담은 준비 결과를 생성
	public static PaymentCancelPreparation completed(PaymentCancelResponse response) {
		return new PaymentCancelPreparation(null, response);
	}

	// 기존 취소 응답으로 완료 가능한 상태인지 확인
	public boolean isCompleted() {
		return existingResponse != null;
	}
}
