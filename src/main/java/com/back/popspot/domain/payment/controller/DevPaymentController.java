package com.back.popspot.domain.payment.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.payment.dto.DevPaymentCreateRequest;
import com.back.popspot.domain.payment.dto.DevPaymentCreateResponse;
import com.back.popspot.domain.payment.service.DevPaymentService;
import com.back.popspot.global.response.CommonApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@Profile("dev")
@RequiredArgsConstructor
@RequestMapping("/api/dev/payments")
public class DevPaymentController {
	private final DevPaymentService devPaymentService;

	@PostMapping
	public ResponseEntity<CommonApiResponse<DevPaymentCreateResponse>> create(
		@AuthenticationPrincipal Long userId,
		@Valid @RequestBody DevPaymentCreateRequest request
	) {
		DevPaymentCreateResponse response = devPaymentService.create(userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(CommonApiResponse.created("개발용 결제 주문이 생성되었습니다.", response));
	}
}
