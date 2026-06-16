package com.back.popspot.domain.reservation.dto.request;

import jakarta.validation.constraints.NotNull;

public record ReservationCreateRequest(
	@NotNull Long slotId
) {
}
