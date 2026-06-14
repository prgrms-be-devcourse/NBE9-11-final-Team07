package com.back.popspot.domain.reservation.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	// 같은 유저의 같은 슬롯 예약 존재 여부 확인
	boolean existsByMemberIdAndSlotId(Long memberId, Long slotId);

	// 확정 예약만 취소 상태로 변경
	@Modifying
	@Query("""
		update Reservation reservation
		set reservation.status = :canceledStatus,
			reservation.canceledAt = :canceledAt
		where reservation.id = :reservationId
		and reservation.status = :confirmedStatus
		""")
	int cancelConfirmedReservation(
		@Param("reservationId") Long reservationId,
		@Param("confirmedStatus") ReservationStatus confirmedStatus,
		@Param("canceledStatus") ReservationStatus canceledStatus,
		@Param("canceledAt") LocalDateTime canceledAt
	);
}
