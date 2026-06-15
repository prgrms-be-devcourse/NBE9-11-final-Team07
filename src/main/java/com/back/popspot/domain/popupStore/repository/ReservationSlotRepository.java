package com.back.popspot.domain.popupStore.repository;

import java.time.LocalDate;
import java.util.List;
import java.time.LocalTime;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
