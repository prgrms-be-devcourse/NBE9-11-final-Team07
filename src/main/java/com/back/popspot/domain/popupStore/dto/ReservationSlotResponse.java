package com.back.popspot.domain.popupStore.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;

/**
 * 예약 슬롯 조회 응답. available 은 잔여 정원 여부(reservedCount &lt; capacity)로 계산된다.
 */
public record ReservationSlotResponse(
		Long slotId,
		LocalDate slotDate,
		LocalTime startTime,
		int capacity,
		int reservedCount,
		boolean available
) {
	public static ReservationSlotResponse from(ReservationSlot slot) {
		return new ReservationSlotResponse(
				slot.getId(),
				slot.getSlotDate(),
				slot.getStartTime(),
				slot.getCapacity(),
				slot.getReservedCount(),
				slot.getReservedCount() < slot.getCapacity()
		);
	}
}
