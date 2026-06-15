package com.back.popspot.domain.reservation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.reservation.dto.request.ReservationCreateRequest;
import com.back.popspot.domain.reservation.dto.request.ReservationPaymentRequest;
import com.back.popspot.domain.reservation.dto.response.ReservationPaymentResponse;
import com.back.popspot.domain.reservation.dto.response.ReservationCreateResponse;
import com.back.popspot.domain.reservation.service.ReservationService;
import com.back.popspot.global.response.CommonApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/reservations")
public class ReservationController {

	private final ReservationService reservationService;

	@PostMapping
	public ResponseEntity<CommonApiResponse<ReservationCreateResponse>> createReservation(
		@Valid @RequestBody ReservationCreateRequest request
	) {
		ReservationCreateResponse response = reservationService.createReservation(request);

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(CommonApiResponse.created("예약 생성이 완료되었습니다.", response));
	}

	@PostMapping("/{reservationId}/payments")
	public ResponseEntity<CommonApiResponse<ReservationPaymentResponse>> startReservationPayment(
		@PathVariable Long reservationId,
		@AuthenticationPrincipal Long userId,
		@Valid @RequestBody ReservationPaymentRequest request
	) {
		ReservationPaymentResponse response = reservationService.startReservationPayment(reservationId, userId, request);

		return ResponseEntity.ok(CommonApiResponse.success(response));
	}

	@DeleteMapping("/{reservationId}")
	public ResponseEntity<CommonApiResponse<Void>> cancelReservation(
		@PathVariable Long reservationId,
		@AuthenticationPrincipal Long userId
	) {
		reservationService.cancelReservation(reservationId, userId);

		return ResponseEntity.ok(CommonApiResponse.successMessage("예약 취소가 완료되었습니다."));
	}
}
