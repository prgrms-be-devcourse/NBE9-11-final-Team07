package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.reservation.entity.ReservationCancelPool;
import com.back.popspot.domain.reservation.entity.ReservationCancelPoolStatus;
import com.back.popspot.domain.reservation.repository.ReservationCancelPoolRepository;
import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationReopenService {

	private final ReservationCancelPoolRepository reservationCancelPoolRepository;
	private final RedisTemplate<String, Long> redisTemplate;

	@Transactional
	public void reopenDuePools() {
		LocalDateTime now = LocalDateTime.now();
		List<ReservationCancelPool> pools =
			reservationCancelPoolRepository.findByReopenStatusAndReopenAtLessThanEqualAndPendingCountGreaterThan(
				ReservationCancelPoolStatus.SCHEDULED,
				now,
				0
			);

		for (ReservationCancelPool pool : pools) {
			reopen(pool);
		}
	}

	private void reopen(ReservationCancelPool pool) {
		int claimed = reservationCancelPoolRepository.claimOpening(
			pool.getId(),
			ReservationCancelPoolStatus.SCHEDULED,
			ReservationCancelPoolStatus.OPENING
		);
		if (claimed == 0) {
			return;
		}

		try {
			int pendingCount = pool.getPendingCount();
			redisTemplate.opsForValue()
				.increment(RedisKeys.reservationSlotRemaining(pool.getSlot().getId()), pendingCount);
			pool.openPending();
		} catch (RuntimeException e) {
			log.error(
				"[RESERVATION_REOPEN_FAILED] 취소 예약 재오픈 Redis 반영 실패: poolId={}, slotId={}, reopenAt={}, pendingCount={}",
				pool.getId(),
				pool.getSlot().getId(),
				pool.getReopenAt(),
				pool.getPendingCount(),
				e
			);
			pool.fail();
		}
	}
}
