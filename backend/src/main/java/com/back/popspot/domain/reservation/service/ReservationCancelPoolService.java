package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.reservation.entity.ReservationCancelPoolStatus;
import com.back.popspot.domain.reservation.repository.ReservationCancelPoolRepository;
import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationCancelPoolService {

	private final ReservationCancelPoolRepository reservationCancelPoolRepository;
	private final ReservationReopenPolicy reservationReopenPolicy;
	private final RedisTemplate<String, Long> redisTemplate;

	@Transactional
	public void accrue(ReservationSlot slot, LocalDateTime canceledAt) {
		LocalDateTime reopenAt = reservationReopenPolicy.calculateReopenAt(canceledAt);
		LocalDateTime slotStartAt = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
		LocalDateTime reservationEndAt = slot.getPopupStore().getReservationEndAt();
		LocalDateTime reservableUntil = slotStartAt.isBefore(reservationEndAt) ? slotStartAt : reservationEndAt;

		if (reopenAt.isBefore(reservableUntil)) {
			reservationCancelPoolRepository.accruePendingCount(slot.getId(), reopenAt);
			return;
		}

		if (canceledAt.isBefore(reservableUntil)) {
			if (!TransactionSynchronizationManager.isSynchronizationActive()) {
				redisTemplate.opsForValue().increment(RedisKeys.reservationSlotRemaining(slot.getId()));
				return;
			}

			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					redisTemplate.opsForValue().increment(RedisKeys.reservationSlotRemaining(slot.getId()));
				}
			});
		}
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
