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

	// 예약 가능 인원이 남아 있는 경우에만 슬롯 예약 수 증가
	@Modifying
	@Query("""
		update ReservationSlot slot
		set slot.reservedCount = slot.reservedCount + 1
		where slot.id = :slotId
		and slot.reservedCount < slot.capacity
		""")
	int increaseReservedCountIfAvailable(@Param("slotId") Long slotId);

	// 취소 가능한 예약 1건에 대해 슬롯 예약 수 감소
	@Modifying
	@Query("""
		update ReservationSlot slot
		set slot.reservedCount = slot.reservedCount - 1
		where slot.id = :slotId
		and slot.reservedCount > 0
		""")
	int decreaseReservedCount(@Param("slotId") Long slotId);
}
