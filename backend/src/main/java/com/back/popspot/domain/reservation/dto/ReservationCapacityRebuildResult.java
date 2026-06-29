package com.back.popspot.domain.reservation.dto;

public record ReservationCapacityRebuildResult(
	Long slotId,
	int capacity,
	long activeReservationCount,
	Long previousRedisRemaining,
	long rebuiltRedisRemaining
) {

	public static ReservationCapacityRebuildResult from(
		Long slotId,
		int capacity,
		long activeReservationCount,
		Long previousRedisRemaining,
		long rebuiltRedisRemaining
	) {
		return new ReservationCapacityRebuildResult(
			slotId,
			capacity,
			activeReservationCount,
			previousRedisRemaining,
			rebuiltRedisRemaining
		);
	}
}
