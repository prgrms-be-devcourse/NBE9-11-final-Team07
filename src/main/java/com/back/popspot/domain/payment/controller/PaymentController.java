package com.back.popspot.domain.payment.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.payment.client.TossPaymentsClient;
import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

// 로컬 API 요청을 받음
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

	private final TossPaymentsClient tossPaymentsClient;

	@PostMapping("/confirm")
	public JsonNode confirm(
		@Valid @RequestBody PaymentConfirmRequest request
	) {
		// 실제 구현에서는 DB 주문번호와 금액을 먼저 검증해야 한다
		return tossPaymentsClient.confirm(request);
	}
}
