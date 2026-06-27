package com.back.popspot.domain.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.reservation.entity.ReservationWaitlist;

public interface ReservationWaitlistRepository extends JpaRepository<ReservationWaitlist, Long> {

	boolean existsByUserIdAndSlotId(Long userId, Long slotId);

	long countBySlotId(Long slotId);

	long deleteByUserIdAndSlotId(Long userId, Long slotId);
}
