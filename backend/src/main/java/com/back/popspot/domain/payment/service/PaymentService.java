package com.back.popspot.domain.payment.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.back.popspot.domain.payment.client.TossPaymentsClient;
import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentTransactionService paymentTransactionService;
	private final TossPaymentsClient tossPaymentsClient;

	public PaymentConfirmResponse confirm(PaymentConfirmRequest request) {
		Optional<PaymentConfirmResponse> existingPayment = paymentTransactionService.prepare(request);
		if (existingPayment.isPresent()) {
			return existingPayment.get();
		}

		JsonNode tossResponse = tossPaymentsClient.confirm(request);
		validateTossConfirmResponse(tossResponse, request);

		return paymentTransactionService.complete(request, extractApprovedAt(tossResponse));
	}

	private void validateTossConfirmResponse(JsonNode tossResponse, PaymentConfirmRequest request) {
		if (!"DONE".equals(tossResponse.path("status").asString(null))
			|| !request.paymentKey().equals(tossResponse.path("paymentKey").asString(null))
			|| !request.orderId().equals(tossResponse.path("orderId").asString(null))
			|| !String.valueOf(request.amount()).equals(tossResponse.path("totalAmount").asString(null))
			|| tossResponse.path("approvedAt").asString(null) == null
			|| tossResponse.path("approvedAt").asString(null).isBlank()) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_RESPONSE_MISMATCH);
		}
	}

	private LocalDateTime extractApprovedAt(JsonNode tossResponse) {
		String approvedAt = tossResponse.path("approvedAt").asString(null);
		try {
			return OffsetDateTime.parse(approvedAt).toLocalDateTime();
		} catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_RESPONSE_MISMATCH);
		}
	}
}
