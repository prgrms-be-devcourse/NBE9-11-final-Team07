package com.back.popspot.domain.payment.entity;

public enum PaymentStatus {
	READY,
	CONFIRMING,
	PAID,
	FAILED,
	CANCELING,
	CANCEL_FAILED,
	COMPENSATING,
	COMPENSATION_FAILED,
	CANCELED
}
