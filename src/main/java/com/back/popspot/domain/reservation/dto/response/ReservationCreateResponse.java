package com.back.popspot.domain.reservation.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;

public record ReservationCreateResponse(
	Long reservationId,
	ReservationStatus status,
	LocalDateTime heldUntil,
	Long slotId,
	LocalDate slotDate,
	LocalTime startTime
) {
	public static ReservationCreateResponse from(Reservation reservation) {
		ReservationSlot slot = reservation.getSlot();

		return new ReservationCreateResponse(
			reservation.getId(),
			reservation.getStatus(),
			reservation.getHeldUntil(),
			slot.getId(),
			slot.getSlotDate(),
			slot.getStartTime()
		);
	}
}
