package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.stereotype.Component;

@Component
public class ReservationReopenPolicy {

	private static final LocalTime REOPEN_TIME = LocalTime.of(19, 0);
	private static final long CUTOFF_BUFFER_MINUTES = 5L;

	public LocalDateTime calculateReopenAt(LocalDateTime canceledAt) {
		LocalDateTime todayReopenAt = canceledAt.toLocalDate().atTime(REOPEN_TIME);
		LocalDateTime todayCutoffAt = todayReopenAt.minusMinutes(CUTOFF_BUFFER_MINUTES);
		if (canceledAt.isBefore(todayCutoffAt)) {
			return todayReopenAt;
		}
		return todayReopenAt.plusDays(1);
	}
}
