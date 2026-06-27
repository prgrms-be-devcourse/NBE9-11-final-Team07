package com.back.popspot.domain.payment.dto;

import java.time.LocalDateTime;

import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;

public record PaymentConfirmResponse(
	Long paymentId,
	PaymentType paymentType,
	String orderId,
	String paymentKey,
	String orderName,
	long amount,
	PaymentStatus status,
	LocalDateTime approvedAt
) {

	// 결제 엔티티로 결제 승인 응답을 생성
	public static PaymentConfirmResponse from(Payment payment) {
		return new PaymentConfirmResponse(
			payment.getId(),
			payment.getPaymentType(),
			payment.getOrderId(),
			payment.getPaymentKey(),
			payment.getOrderName(),
			payment.getAmount(),
			payment.getStatus(),
			payment.getApprovedAt()
		);
	}
}
