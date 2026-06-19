package com.back.popspot.domain.popupStore.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

public record ReservationSlotUpdateRequest(
	LocalDate slotDate,
	LocalTime startTime,
	@Min(1) Integer capacity
) {
	@AssertTrue
	public boolean hasAnyField() {
		return slotDate != null || startTime != null || capacity != null;
	}
}
