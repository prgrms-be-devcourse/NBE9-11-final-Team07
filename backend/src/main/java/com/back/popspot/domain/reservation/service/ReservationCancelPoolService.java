package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.reservation.entity.ReservationCancelPoolStatus;
import com.back.popspot.domain.reservation.repository.ReservationCancelPoolRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationCancelPoolService {

	private final ReservationCancelPoolRepository reservationCancelPoolRepository;
	private final ReservationReopenPolicy reservationReopenPolicy;

	@Transactional
	public void accrue(Long slotId, LocalDateTime canceledAt) {
		LocalDateTime reopenAt = reservationReopenPolicy.calculateReopenAt(canceledAt);
		reservationCancelPoolRepository.accruePendingCount(slotId, reopenAt);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean claimOpeningInTx(Long poolId) {
		return reservationCancelPoolRepository.claimOpening(
			poolId,
			ReservationCancelPoolStatus.SCHEDULED,
			ReservationCancelPoolStatus.OPENING
		) == 1;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean markOpenedInTx(Long poolId) {
		return reservationCancelPoolRepository.findById(poolId)
			.filter(pool -> pool.getReopenStatus() == ReservationCancelPoolStatus.OPENING)
			.map(pool -> {
				pool.openPending();
				return true;
			})
			.orElseGet(() -> {
				log.warn(
					"[RESERVATION_REOPEN_MARK_OPENED_SKIPPED] 취소 예약 재오픈 OPENED 처리 조건 불일치: poolId={}",
					poolId
				);
				return false;
			});
	}
}
