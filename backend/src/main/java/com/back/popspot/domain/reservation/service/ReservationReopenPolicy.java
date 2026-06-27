package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.stereotype.Component;

@Component
public class ReservationReopenPolicy {

	private static final LocalTime REOPEN_TIME = LocalTime.of(19, 0);

	public LocalDateTime calculateReopenAt(LocalDateTime canceledAt) {
		LocalDateTime todayReopenAt = canceledAt.toLocalDate().atTime(REOPEN_TIME);
		if (canceledAt.isBefore(todayReopenAt)) {
			return todayReopenAt;
		}
		return todayReopenAt.plusDays(1);
	}
}
