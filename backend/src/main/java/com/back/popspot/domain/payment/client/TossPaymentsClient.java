package com.back.popspot.domain.payment.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentCancelCommand;
import com.back.popspot.domain.payment.dto.TossPaymentCancelRequest;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

// 토스 승인 API 요청 실행
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

	private final RestClient tossPaymentsRestClient;

	public JsonNode confirm(PaymentConfirmRequest request) {
		return tossPaymentsRestClient.post()
			.uri("/v1/payments/confirm")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.body(JsonNode.class);
	}

	public JsonNode cancel(PaymentCancelCommand command) {
		return tossPaymentsRestClient.post()
			.uri("/v1/payments/{paymentKey}/cancel", command.paymentKey())
			.contentType(MediaType.APPLICATION_JSON)
			.header("Idempotency-Key", command.idempotencyKey())
			.body(new TossPaymentCancelRequest(command.cancelReason()))
			.retrieve()
			.body(JsonNode.class);
	}
}
