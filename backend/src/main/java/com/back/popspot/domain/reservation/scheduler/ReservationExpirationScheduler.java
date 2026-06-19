package com.back.popspot.domain.reservation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.back.popspot.domain.reservation.service.ReservationExpirationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

	private static final long FIXED_DELAY_MILLIS = 60000L;

	private final ReservationExpirationService reservationExpirationService;

	@Scheduled(fixedDelay = FIXED_DELAY_MILLIS)
	public void expireReservations() {
		reservationExpirationService.expireExpiredReservations();
	}
}
