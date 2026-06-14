package com.back.popspot.domain.reservation.dto.response;

import java.time.LocalDateTime;

import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;

public record ReservationCancelResponse(
	Long reservationId,
	ReservationStatus status,
	LocalDateTime canceledAt
) {
	public static ReservationCancelResponse from(Reservation reservation) {
		return new ReservationCancelResponse(
			reservation.getId(),
			reservation.getStatus(),
			reservation.getCanceledAt()
		);
	}
}
