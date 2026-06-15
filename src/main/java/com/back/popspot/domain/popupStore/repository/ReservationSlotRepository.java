package com.back.popspot.domain.popupStore.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, Long> {

	// 특정 팝업스토어의 특정 날짜 예약 슬롯 목록
	List<ReservationSlot> findByPopupStoreIdAndSlotDate(Long popupStoreId, LocalDate slotDate);

	boolean existsByPopupStoreIdAndSlotDateAndStartTimeAndIdNot(
		Long popupStoreId,
		LocalDate slotDate,
		LocalTime startTime,
		Long id
	);

	@Modifying
	@Query("""
		update ReservationSlot slot
		set slot.reservedCount = slot.reservedCount + 1
		where slot.id = :slotId
		and slot.reservedCount < slot.capacity
		""")
	int increaseReservedCountIfAvailable(@Param("slotId") Long slotId);
}
