package com.back.popspot.domain.popupStore.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, Long> {

	// 특정 팝업스토어의 특정 날짜 예약 슬롯 목록
	List<ReservationSlot> findByPopupStoreIdAndSlotDate(Long popupStoreId, LocalDate slotDate);

	// 정원 복구 배치 전용 — 주어진 팝업들에 속한 예약 슬롯 id 목록 (복구엔 슬롯 id 목록이면 충분)
	@Query("select slot.id from ReservationSlot slot where slot.popupStore.id in :popupStoreIds")
	List<Long> findIdsByPopupStoreIdIn(@Param("popupStoreIds") Collection<Long> popupStoreIds);

	// 예약 검증 시 lazy 연관관계(popupStore)까지 한 번에 로딩한다.
	// createReservation 은 트랜잭션 밖에서 검증하므로 popupStore 가 초기화된 상태여야 한다.
	@Query("select slot from ReservationSlot slot join fetch slot.popupStore where slot.id = :slotId")
	Optional<ReservationSlot> findByIdWithPopupStore(@Param("slotId") Long slotId);

	void deleteByPopupStoreId(Long popupStoreId);

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
