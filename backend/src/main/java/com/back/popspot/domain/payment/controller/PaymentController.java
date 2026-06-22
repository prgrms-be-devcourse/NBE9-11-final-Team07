package com.back.popspot.domain.payment.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.dto.PaymentCancelRequest;
import com.back.popspot.domain.payment.dto.PaymentCancelResponse;
import com.back.popspot.domain.payment.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
// 로컬 API 요청을 받음
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

	private final PaymentService paymentService;

	@PostMapping("/confirm")
	public PaymentConfirmResponse confirm(
		@Valid @RequestBody PaymentConfirmRequest request
	) {
		return paymentService.confirm(request);
	}

	@PostMapping("/{paymentId}/cancel")
	public PaymentCancelResponse cancel(
		@PathVariable Long paymentId,
		@AuthenticationPrincipal Long userId,
		@Valid @RequestBody PaymentCancelRequest request
	) {
		return paymentService.cancel(paymentId, userId, request);
	}
}
