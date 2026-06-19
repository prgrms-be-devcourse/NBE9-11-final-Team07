package com.back.popspot.domain.reservation.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;

public record MyReservationResponse(
	Long reservationId,
	String popupName,
	String location,
	LocalDate reservationDate,
	LocalTime reservationTime,
	Integer price,
	ReservationStatus status
) {

	public static MyReservationResponse from(Reservation reservation) {
		ReservationSlot slot = reservation.getSlot();
		PopupStore popupStore = slot.getPopupStore();

		return new MyReservationResponse(
			reservation.getId(),
			popupStore.getTitle(),
			popupStore.getLocation(),
			slot.getSlotDate(),
			slot.getStartTime(),
			resolvePrice(popupStore),
			reservation.getStatus()
		);
	}

	private static Integer resolvePrice(PopupStore popupStore) {
		if (popupStore.getFeeType() == PopupFeeType.FREE) {
			return 0;
		}

		return popupStore.getPrice();
	}
}
