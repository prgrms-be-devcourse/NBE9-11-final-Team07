package com.back.popspot.domain.reservation.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.reservation.dto.response.MyReservationResponse;
import com.back.popspot.domain.reservation.service.ReservationService;
import com.back.popspot.global.response.CommonApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MyReservationController {

	private final ReservationService reservationService;

	@GetMapping("/me/reservations")
	public ResponseEntity<CommonApiResponse<Page<MyReservationResponse>>> getMyReservations(
		@AuthenticationPrincipal Long userId,
		Pageable pageable
	) {
		Page<MyReservationResponse> response = reservationService.getMyReservations(userId, pageable);

		return ResponseEntity.ok(CommonApiResponse.success(response));
	}
}
