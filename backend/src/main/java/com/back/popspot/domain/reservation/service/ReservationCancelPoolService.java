package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.reservation.repository.ReservationCancelPoolRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationCancelPoolService {

	private final ReservationCancelPoolRepository reservationCancelPoolRepository;
	private final ReservationReopenPolicy reservationReopenPolicy;

	@Transactional
	public void accrue(Long slotId, LocalDateTime canceledAt) {
		LocalDateTime reopenAt = reservationReopenPolicy.calculateReopenAt(canceledAt);
		reservationCancelPoolRepository.accruePendingCount(slotId, reopenAt);
	}
}
