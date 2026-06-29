package com.back.popspot.domain.reservation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.back.popspot.domain.reservation.service.ReservationReopenService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReservationReopenScheduler {

	private static final long FIXED_DELAY_MILLIS = 60_000L;

	private final ReservationReopenService reservationReopenService;

	@Scheduled(fixedDelay = FIXED_DELAY_MILLIS)
	public void reopenCanceledReservations() {
		reservationReopenService.reopenDuePools();
	}
}
