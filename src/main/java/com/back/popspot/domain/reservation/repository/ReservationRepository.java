package com.back.popspot.domain.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.reservation.entity.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	boolean existsBySlotId(Long slotId);
}
