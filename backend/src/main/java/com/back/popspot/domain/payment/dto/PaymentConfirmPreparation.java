package com.back.popspot.domain.payment.dto;

public record PaymentConfirmPreparation(
	boolean confirmRequired,
	PaymentConfirmResponse existingResponse
) {
	// 토스 승인 요청이 필요한 준비 결과를 생성
	public static PaymentConfirmPreparation required() {
		return new PaymentConfirmPreparation(true, null);
	}

	// 이미 완료된 결제 응답을 담은 준비 결과를 생성
	public static PaymentConfirmPreparation completed(PaymentConfirmResponse response) {
		return new PaymentConfirmPreparation(false, response);
	}

	// 기존 결제 응답으로 완료 가능한 상태인지 확인
	public boolean isCompleted() {
		return existingResponse != null;
	}
}
