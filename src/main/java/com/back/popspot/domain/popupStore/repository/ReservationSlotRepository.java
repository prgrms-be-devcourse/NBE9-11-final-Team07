package com.back.popspot.domain.popupStore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, Long> {

	@Modifying
	@Query("""
		update ReservationSlot slot
		set slot.reservedCount = slot.reservedCount + 1
		where slot.id = :slotId
		and slot.reservedCount < slot.capacity
		""")
	int increaseReservedCountIfAvailable(@Param("slotId") Long slotId);
}
