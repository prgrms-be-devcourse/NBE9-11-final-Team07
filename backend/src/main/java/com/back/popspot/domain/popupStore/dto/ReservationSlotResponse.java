package com.back.popspot.domain.popupStore.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;

public record ReservationSlotResponse(
		Long slotId,
		LocalDate slotDate,
		LocalTime startTime,
		int capacity,
		int reservedCount,
		boolean available
) {
	public static ReservationSlotResponse from(ReservationSlot slot) {
		return from(slot, slot.getReservedCount() < slot.getCapacity());
	}

	public static ReservationSlotResponse from(ReservationSlot slot, boolean available) {
		return new ReservationSlotResponse(
				slot.getId(),
				slot.getSlotDate(),
				slot.getStartTime(),
				slot.getCapacity(),
				slot.getReservedCount(),
				available
		);
	}
}
