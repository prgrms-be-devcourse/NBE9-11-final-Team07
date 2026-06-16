package com.back.popspot.domain.payment.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.payment.client.TossPaymentsClient;
import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final TossPaymentsClient tossPaymentsClient;

	@Transactional
	public PaymentConfirmResponse confirm(PaymentConfirmRequest request) {
		Payment payment = paymentRepository.findByOrderId(request.orderId())
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

		validateAmount(payment, request.amount());

		if (payment.isPaid()) {
			validatePaymentKey(payment, request.paymentKey());
			return PaymentConfirmResponse.from(payment);
		}

		if (!payment.isReady()) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_NOT_ALLOWED_STATUS);
		}

		JsonNode tossResponse = tossPaymentsClient.confirm(request);
		payment.complete(
			request.paymentKey(),
			extractApprovedAt(tossResponse)
		);

		return PaymentConfirmResponse.from(payment);
	}

	private void validateAmount(Payment payment, long requestAmount) {
		if (payment.getAmount() != requestAmount) {
			throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
		}
	}

	private void validatePaymentKey(Payment payment, String requestPaymentKey) {
		if (!requestPaymentKey.equals(payment.getPaymentKey())) {
			throw new BusinessException(ErrorCode.PAYMENT_KEY_MISMATCH);
		}
	}

	private LocalDateTime extractApprovedAt(JsonNode tossResponse) {
		String approvedAt = tossResponse.path("approvedAt").asString(null);
		if (approvedAt == null || approvedAt.isBlank()) {
			return LocalDateTime.now();
		}

		return OffsetDateTime.parse(approvedAt).toLocalDateTime();
	}
}
