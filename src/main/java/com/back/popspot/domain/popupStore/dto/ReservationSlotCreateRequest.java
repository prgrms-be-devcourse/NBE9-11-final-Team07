package com.back.popspot.domain.popupStore.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

/**
 * 예약 슬롯 생성 요청. reservedCount 는 서버에서 0 으로 초기화한다.
 */
public record ReservationSlotCreateRequest(
		@NotNull LocalDate slotDate,
		@NotNull LocalTime startTime,
		@NotNull Integer capacity
) {
}
