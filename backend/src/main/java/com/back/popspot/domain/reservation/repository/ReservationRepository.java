package com.back.popspot.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	// 같은 유저의 같은 슬롯 예약 존재 여부 확인
	boolean existsByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull(Long userId, Long slotId);

	// 사용자의 확정/취소 예약 목록 조회
	Page<Reservation> findByUserIdAndStatusIn(Long userId, Collection<ReservationStatus> statuses, Pageable pageable);

	long countBySlotIdAndStatusIn(Long slotId, Collection<ReservationStatus> statuses);

	// 만료 시간이 지난 선점 예약 조회 (slot 함께 로딩 — 트랜잭션 밖 접근 대비)
	@Query("""
   select reservation
   from Reservation reservation
   join fetch reservation.slot
   where reservation.status = :status
   and reservation.heldUntil < :now
   """)
	List<Reservation> findByStatusAndHeldUntilBefore(
		@Param("status") ReservationStatus status,
		@Param("now") LocalDateTime now
	);

	// 확정 예약만 취소 상태로 변경
	@Modifying
	@Query("""
		update Reservation reservation
		set reservation.status = :canceledStatus,
			reservation.canceledAt = :canceledAt,
			reservation.activeUniqueKey = null
		where reservation.id = :reservationId
		and reservation.status = :confirmedStatus
		""")
	int cancelConfirmedReservation(
		@Param("reservationId") Long reservationId,
		@Param("confirmedStatus") ReservationStatus confirmedStatus,
		@Param("canceledStatus") ReservationStatus canceledStatus,
		@Param("canceledAt") LocalDateTime canceledAt
	);

	// HELD 상태이고 만료 시간이 지난 예약만 만료 상태로 변경
	@Modifying(flushAutomatically = true)
	@Query("""
		update Reservation reservation
		set reservation.status = :expiredStatus,
			reservation.activeUniqueKey = null
		where reservation.id = :reservationId
		and reservation.status = :heldStatus
		and reservation.heldUntil < :now
		""")
	int expireHeldReservation(
		@Param("reservationId") Long reservationId,
		@Param("heldStatus") ReservationStatus heldStatus,
		@Param("expiredStatus") ReservationStatus expiredStatus,
		@Param("now") LocalDateTime now
	);
}
