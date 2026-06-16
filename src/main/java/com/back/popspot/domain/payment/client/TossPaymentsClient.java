package com.back.popspot.domain.payment.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;

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
}
