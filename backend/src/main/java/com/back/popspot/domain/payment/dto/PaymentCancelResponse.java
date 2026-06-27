package com.back.popspot.domain.payment.dto;

import java.time.LocalDateTime;

import com.back.popspot.domain.payment.entity.PaymentRefund;
import com.back.popspot.domain.payment.entity.PaymentRefundStatus;
import com.back.popspot.domain.payment.entity.PaymentStatus;

public record PaymentCancelResponse(
	Long refundId,
	Long paymentId,
	String paymentKey,
	String orderId,
	long cancelAmount,
	PaymentStatus paymentStatus,
	PaymentRefundStatus refundStatus,
	String transactionKey,
	LocalDateTime canceledAt
) {
	// 환불 엔티티로 결제 취소 응답을 생성
	public static PaymentCancelResponse from(PaymentRefund refund) {
		return new PaymentCancelResponse(
			refund.getId(),
			refund.getPayment().getId(),
			refund.getPayment().getPaymentKey(),
			refund.getPayment().getOrderId(),
			refund.getRefundAmount(),
			refund.getPayment().getStatus(),
			refund.getStatus(),
			refund.getTransactionKey(),
			refund.getCompletedAt()
		);
	}
}
