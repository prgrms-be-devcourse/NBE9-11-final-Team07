package com.back.popspot.global.admin.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.global.admin.dto.SlotStockResetRequest;
import com.back.popspot.global.admin.dto.SlotStockResetResponse;
import com.back.popspot.global.admin.dto.SlotStockResetResponse.DeletedRows;
import com.back.popspot.global.admin.dto.SlotStockResetResponse.GrantedProceedFlags;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.queue.config.WaitingQueueProperties;
import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 부하 테스트를 반복 실행할 수 있도록 슬롯 재고를 지정한 값으로 되돌리는 서비스.
 *
 * <p>Redis 카운터와 DB 를 함께 초기화한다. 둘 다 건드리는 이유는, 재고의 source of truth 인
 * Redis remaining 만 되돌리면 DB 에 직전 회차의 예약 행이 남아 중복 예약 검증
 * ({@code existsByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull})에 걸려 다음 회차가 정상 측정되지 않기 때문이다.
 *
 * <p>순서는 DB 정리 → Redis 세팅이다. 중간에 실패하더라도 연산이 모두 멱등이므로 그대로 다시 호출하면 된다.
 *
 * <p>부하 테스트 전용이므로 운영(prod) 프로파일에서는 빈 자체가 등록되지 않는다.
 */
@Slf4j
@Service
@Profile("!prod")
@RequiredArgsConstructor
public class SlotStockResetService {

	// 오타로 억대 범위가 들어와 Redis 를 채우는 사고를 막는 상한
	private static final long MAX_PROCEED_RANGE_SIZE = 1_000_000L;

	private final ReservationSlotRepository reservationSlotRepository;
	private final SlotStockDbResetService slotStockDbResetService;
	private final ProceedFlagGrantService proceedFlagGrantService;
	private final WaitingQueueProperties waitingQueueProperties;
	private final RedisTemplate<String, Long> redisTemplate;

	public SlotStockResetResponse reset(Long slotId, SlotStockResetRequest request) {
		ReservationSlot slot = findSlot(slotId);

		long popupStoreId = slot.getPopupStore().getId();

		int capacity = request.capacity() != null ? request.capacity() : slot.getCapacity();
		int remaining = request.remaining() != null ? request.remaining() : capacity;
		if (remaining > capacity) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
		validateProceedRange(request);

		DeletedRows deleted = request.purgeReservationsOrDefault()
			? slotStockDbResetService.purgeSlotReservations(slotId)
			: DeletedRows.none();

		int reservedCount = capacity - remaining;
		slotStockDbResetService.resetSlotCounters(slotId, capacity, reservedCount);

		String remainingKey = RedisKeys.reservationSlotRemaining(slotId);
		Long previousRedisRemaining = redisTemplate.opsForValue().get(remainingKey);
		Long ttlSeconds = writeRemaining(slot, remainingKey, remaining);

		GrantedProceedFlags proceed = grantProceedFlags(popupStoreId, request);

		log.info(
			"[SLOT_STOCK_RESET] 슬롯 재고 리셋: slotId={}, capacity={}, previousRedisRemaining={}, resetRemaining={}, "
				+ "deletedPayments={}, deletedReservations={}, deletedWaitlists={}, deletedCancelPools={}",
			slotId,
			capacity,
			previousRedisRemaining,
			remaining,
			deleted.payments(),
			deleted.reservations(),
			deleted.waitlists(),
			deleted.cancelPools()
		);

		return new SlotStockResetResponse(
			slotId,
			popupStoreId,
			capacity,
			previousRedisRemaining,
			remaining,
			reservedCount,
			ttlSeconds,
			deleted,
			proceed
		);
	}

	// 한쪽 경계만 주면 의도를 알 수 없으므로 조용히 넘기지 않고 거절한다.
	private void validateProceedRange(SlotStockResetRequest request) {
		if (request.hasPartialProceedRange()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
		if (!request.hasProceedRange()) {
			return;
		}
		if (request.proceedUserIdFrom() > request.proceedUserIdTo()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
		long size = request.proceedUserIdTo() - request.proceedUserIdFrom() + 1;
		if (size > MAX_PROCEED_RANGE_SIZE) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
	}

	/**
	 * 예약 성공 시 소각되는 입장 허가(proceed flag)를 유저 id 범위로 다시 심는다.
	 * 범위를 주지 않았으면 아무것도 하지 않고 null 을 돌려준다.
	 */
	private GrantedProceedFlags grantProceedFlags(long popupStoreId, SlotStockResetRequest request) {
		if (!request.hasProceedRange()) {
			return null;
		}

		long ttlSeconds = request.proceedTtlSeconds() != null
			? request.proceedTtlSeconds()
			: waitingQueueProperties.proceedTtlSeconds();

		long granted = proceedFlagGrantService.grantRange(
			popupStoreId,
			request.proceedUserIdFrom(),
			request.proceedUserIdTo(),
			ttlSeconds
		);

		return new GrantedProceedFlags(
			request.proceedUserIdFrom(),
			request.proceedUserIdTo(),
			granted,
			ttlSeconds
		);
	}

	// popupStore 까지 join fetch 하므로 트랜잭션 밖에서도 closeDate 를 읽을 수 있다.
	private ReservationSlot findSlot(Long slotId) {
		return reservationSlotRepository.findByIdWithPopupStore(slotId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SLOT_NOT_FOUND));
	}

	// 슬롯 생성/재구축과 동일하게 팝업 종료일까지를 TTL 로 건다. 이미 지난 팝업이면 TTL 없이 세팅한다.
	private Long writeRemaining(ReservationSlot slot, String remainingKey, int remaining) {
		LocalDateTime closeDate = slot.getPopupStore().getCloseDate();
		long ttlSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), closeDate);

		if (ttlSeconds > 0) {
			redisTemplate.opsForValue().set(remainingKey, (long)remaining, Duration.ofSeconds(ttlSeconds));
			return ttlSeconds;
		}

		redisTemplate.opsForValue().set(remainingKey, (long)remaining);
		return null;
	}
}
