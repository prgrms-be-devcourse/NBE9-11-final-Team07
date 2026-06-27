package com.back.popspot.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.back.popspot.domain.reservation.entity.ReservationCancelPool;
import com.back.popspot.domain.reservation.entity.ReservationCancelPoolStatus;

public interface ReservationCancelPoolRepository extends JpaRepository<ReservationCancelPool, Long> {

	Optional<ReservationCancelPool> findBySlotIdAndReopenAt(Long slotId, LocalDateTime reopenAt);

	@Modifying
	@Query(value = """
		insert into reservation_cancel_pool
			(slot_id, reopen_at, pending_count, opened_count, reopen_status, created_at, modified_at)
		values
			(:slotId, :reopenAt, 1, 0, 'SCHEDULED', current_timestamp, current_timestamp)
		on duplicate key update
			pending_count = pending_count + 1,
			modified_at = current_timestamp
		""", nativeQuery = true)
	int accruePendingCount(
		@Param("slotId") Long slotId,
		@Param("reopenAt") LocalDateTime reopenAt
	);

	List<ReservationCancelPool> findByReopenStatusAndReopenAtLessThanEqualAndPendingCountGreaterThan(
		ReservationCancelPoolStatus reopenStatus,
		LocalDateTime now,
		int pendingCount
	);

	@Modifying
	@Query("""
		update ReservationCancelPool pool
		set pool.reopenStatus = :openingStatus
		where pool.id = :poolId
		and pool.reopenStatus = :scheduledStatus
		""")
	int claimOpening(
		@Param("poolId") Long poolId,
		@Param("scheduledStatus") ReservationCancelPoolStatus scheduledStatus,
		@Param("openingStatus") ReservationCancelPoolStatus openingStatus
	);

	@Query("""
		select coalesce(sum(pool.pendingCount), 0)
		from ReservationCancelPool pool
		where pool.slot.id = :slotId
		and pool.pendingCount > 0
		""")
	long sumPendingCountBySlotId(@Param("slotId") Long slotId);
}
