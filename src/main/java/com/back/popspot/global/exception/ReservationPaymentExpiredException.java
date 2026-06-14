package com.back.popspot.global.exception;

public class ReservationPaymentExpiredException extends BusinessException {

	public ReservationPaymentExpiredException(ErrorCode errorCode) {
		super(errorCode);
	}
}
