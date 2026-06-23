package com.back.popspot.domain.payment.entity;

public enum PaymentStatus {
	READY,
	CONFIRMING,
	PAID,
	FAILED,
	COMPENSATING,
	COMPENSATION_FAILED,
	CANCELED
}
