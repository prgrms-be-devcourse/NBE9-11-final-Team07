package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.dto.ReservationCapacityRebuildResult;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationCancelPoolRepository;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationCapacityRebuildService {

	private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(
		ReservationStatus.HELD,
		ReservationStatus.CONFIRMED
	);

	private final ReservationSlotRepository reservationSlotRepository;
	private final ReservationRepository reservationRepository;
	private final ReservationCancelPoolRepository reservationCancelPoolRepository;
	private final RedisTemplate<String, Long> redisTemplate;

	@Transactional(readOnly = true)
	public ReservationCapacityRebuildResult rebuildSlotRemaining(Long slotId) {
		// 복구할 슬롯이 실제로 존재하는지 먼저 확인한다.
		ReservationSlot slot = reservationSlotRepository.findByIdWithPopupStore(slotId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SLOT_NOT_FOUND));

		// 슬롯의 총 정원은 Redis가 아니라 DB 슬롯 정보를 기준으로 사용한다.
		int capacity = slot.getCapacity();

		// DB 예약 원장에서 현재 자리를 점유 중인 HELD, CONFIRMED 예약 수만 센다.
		long activeReservationCount = reservationRepository.countBySlotIdAndStatusIn(slotId, ACTIVE_STATUSES);

		LocalDateTime slotStartAt = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
		LocalDateTime reservableUntil = getEarlier(slotStartAt, slot.getPopupStore().getReservationEndAt());
		long pendingCancelCount = reservationCancelPoolRepository.sumScheduledPendingCountBySlotIdAndReopenAtBefore(
			slotId,
			reservableUntil
		);

		// DB 원장 기준 남은 정원에서 아직 공개하지 않은 취소 예약 수량을 제외한다.
		long remaining = capacity - activeReservationCount - pendingCancelCount;

		// DB 기준으로 이미 정원을 초과했다면 Redis 값을 덮어쓰지 않는다.
		if (remaining < 0) {
			log.warn(
				"[RESERVATION_CAPACITY_OVERBOOKING_SUSPECTED] DB 기준 활성 예약 수가 슬롯 정원을 초과: slotId={}, capacity={}, activeReservationCount={}, calculatedRemaining={}, action=REDIS_NOT_MODIFIED",
				slotId,
				capacity,
				activeReservationCount,
				remaining
			);
			throw new BusinessException(ErrorCode.RESERVATION_CAPACITY_OVERBOOKING_SUSPECTED);
		}

		// slotId에 해당하는 Redis remaining key 이름을 구한다.
		String remainingKey = RedisKeys.reservationSlotRemaining(slotId);

		// 운영자가 복구 전후 값을 비교할 수 있도록 현재 Redis 값을 먼저 읽어 둔다.
		Long previousRedisRemaining = redisTemplate.opsForValue().get(remainingKey);

		try {
			// Redis remaining 값을 DB 원장 기준 계산값으로 재설정한다.
			LocalDateTime closeDate = slot.getPopupStore().getCloseDate();
			long ttlSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), closeDate);
			if (ttlSeconds > 0) {
				redisTemplate.opsForValue().set(remainingKey, remaining, ttlSeconds, TimeUnit.SECONDS);
			} else {
				redisTemplate.opsForValue().set(remainingKey, remaining);
			}
		} catch (RuntimeException e) {
			// Redis 쓰기 실패는 DB를 건드리지 않고 실패로 알린다.
			log.error(
				"[RESERVATION_REDIS_REBUILD_FAILED] Redis 예약 잔여 정원 재구축 실패: slotId={}, capacity={}, activeReservationCount={}, calculatedRemaining={}, reason=REDIS_WRITE_FAILED",
				slotId,
				capacity,
				activeReservationCount,
				remaining,
				e
			);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
		}

		// 복구가 끝나면 운영 추적을 위해 핵심 값을 한 줄 로그로 남긴다.
		log.info(
			"[RESERVATION_REDIS_REBUILT] Redis 예약 잔여 정원 재구축 완료: slotId={}, capacity={}, activeReservationCount={}, previousRedisRemaining={}, rebuiltRedisRemaining={}",
			slotId,
			capacity,
			activeReservationCount,
			previousRedisRemaining,
			remaining
		);

		// 테스트나 추후 운영 도구에서 복구 결과를 확인할 수 있도록 값 객체로 반환한다.
		return ReservationCapacityRebuildResult.from(
			slotId,
			capacity,
			activeReservationCount,
			previousRedisRemaining,
			remaining
		);
	}

	private LocalDateTime getEarlier(LocalDateTime first, LocalDateTime second) {
		if (first.isBefore(second)) {
			return first;
		}
		return second;
	}
}
