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

		// 운영자가 복구 전후 값을 비교할 수 있도록 현재 Redis 값을 먼저 읽어 둔다. 조건부 SET의 비교 기준이기도 하다.
		Long previousRedisRemaining = redisTemplate.opsForValue().get(remainingKey);

		// 조건부 SET — 복구가 신규 차감(DECR)을 덮어써 초과판매를 내는 것을 막는다.
		//  1) 현재 Redis 값이 없으면(null): 차감할 게 없던 상태(미초기화/TTL 만료/장애 소실)라 덮어써도
		//     초과판매 위험이 없다. 오히려 복구가 초기화해줘야 정상이므로 그냥 SET.
		//  2) 현재 값이 있고 계산값 > 현재값: "복구가 자리를 실제보다 많이 봤다 = 방금 나간 차감을 아직 못 봤다"는
		//     신호. 덮어쓰면 초과판매가 나므로 SET하지 않고 skip한다(로그만 남기고 예외는 던지지 않는다).
		//  3) 계산값 <= 현재값: 복구가 차감을 반영했거나 하향 교정이므로 덮어써도 초과판매가 안 난다. SET.
		//
		// [알려진 비대칭 부작용] 이 규칙은 "계산값 > 현재값이면 skip"이라 초과판매(remaining 과대)는 막지만,
		// 취소로 자리가 실제로 늘었는데 INCR 실패로 Redis가 과소인 경우 복구가 정당한 상향 교정을 하지 못해
		// 과소판매 쪽으로 보수적으로 치우칠 수 있다. 초과판매(돈·분쟁)가 과소판매(기회손실)보다 나쁘므로
		// 이 트레이드오프는 의도적으로 감수한다. (B 게이트가 켜진 동안엔 신규 DECR이 없어 이 부작용이 완화된다.)
		boolean overwriteWouldInflate = previousRedisRemaining != null && remaining > previousRedisRemaining;

		if (overwriteWouldInflate) {
			// 덮어쓰지 않고 현재값을 유지한다. 결과 DTO의 rebuiltRedisRemaining도 "실제 기록된 값"인 현재값으로 반환해
			// previousRedisRemaining == rebuiltRedisRemaining 이 곧 "이번 복구는 SET을 건너뛰었다"는 신호가 되게 한다.
			log.warn(
				"[RESERVATION_REDIS_REBUILD_SKIPPED] 복구 계산값이 현재 Redis 값보다 커서 덮어쓰지 않음(초과판매 방지): "
					+ "slotId={}, capacity={}, activeReservationCount={}, currentRedisRemaining={}, calculatedRemaining={}",
				slotId,
				capacity,
				activeReservationCount,
				previousRedisRemaining,
				remaining
			);
			return ReservationCapacityRebuildResult.from(
				slotId,
				capacity,
				activeReservationCount,
				previousRedisRemaining,
				previousRedisRemaining
			);
		}

		try {
			// Redis remaining 값을 DB 원장 기준 계산값으로 재설정한다. (현재값이 null이거나 계산값 <= 현재값인 경우만 진입)
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
