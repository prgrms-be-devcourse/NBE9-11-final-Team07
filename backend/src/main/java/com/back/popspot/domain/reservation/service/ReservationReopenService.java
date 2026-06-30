package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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
	private final ReservationCancelPoolService reservationCancelPoolService;
	private final RedisTemplate<String, Long> redisTemplate;

	public void reopenDuePools() {
		LocalDateTime now = LocalDateTime.now();
		List<ReservationCancelPool> pools = reservationCancelPoolRepository.findDuePoolsWithSlot(
			ReservationCancelPoolStatus.SCHEDULED,
			now,
			0
		);

		for (ReservationCancelPool pool : pools) {
			try {
				reopen(pool);
			} catch (RuntimeException e) {
				log.error(
					"[RESERVATION_REOPEN_POOL_FAILED] 취소 예약 재오픈 pool 처리 실패: poolId={}, slotId={}, reopenAt={}, pendingCount={}",
					pool.getId(),
					pool.getSlot().getId(),
					pool.getReopenAt(),
					pool.getPendingCount(),
					e
				);
			}
		}
	}

	private void reopen(ReservationCancelPool pool) {
		boolean claimed = reservationCancelPoolService.claimOpeningInTx(pool.getId());
		if (!claimed) {
			return;
		}

		int pendingCount = pool.getPendingCount();
		Long slotId = pool.getSlot().getId();

		try {
			redisTemplate.opsForValue()
				.increment(RedisKeys.reservationSlotRemaining(slotId), pendingCount);
		} catch (RuntimeException e) {
			log.error(
				"[RESERVATION_REOPEN_REDIS_FAILED] 취소 예약 재오픈 Redis 반영 실패: poolId={}, slotId={}, reopenAt={}, pendingCount={}",
				pool.getId(),
				slotId,
				pool.getReopenAt(),
				pendingCount,
				e
			);
			return;
		}

		boolean opened = reservationCancelPoolService.markOpenedInTx(pool.getId());
		if (!opened) {
			log.warn(
				"[RESERVATION_REOPEN_MARK_OPENED_FAILED] 취소 예약 재오픈 OPENED 처리 실패: poolId={}, slotId={}, reopenAt={}, pendingCount={}",
				pool.getId(),
				slotId,
				pool.getReopenAt(),
				pendingCount
			);
		}
	}
}
