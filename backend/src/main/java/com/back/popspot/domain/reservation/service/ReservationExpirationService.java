package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationExpirationService {

	private final ReservationRepository reservationRepository;
	private final ReservationSlotRepository reservationSlotRepository;
	private final StringRedisTemplate stringRedisTemplate; // [추가]

	@Transactional
	public void expireExpiredReservations() {
		LocalDateTime now = LocalDateTime.now();
		List<Reservation> reservations = reservationRepository.findByStatusAndHeldUntilBefore(
				ReservationStatus.HELD,
				now
		);

		for (Reservation reservation : reservations) {
			expireIfHeldAndExpired(reservation, now);
		}
	}

	@Transactional
	public boolean expireIfHeldAndExpired(Reservation reservation, LocalDateTime now) {
		Long slotId = reservation.getSlot().getId();
		int expiredCount = reservationRepository.expireHeldReservation(
				reservation.getId(),
				ReservationStatus.HELD,
				ReservationStatus.EXPIRED,
				now
		);
		if (expiredCount == 0) {
			return false;
		}

		int updatedCount = reservationSlotRepository.decreaseReservedCount(slotId);
		if (updatedCount == 0) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
		}

		// [추가] DB 성공 확인 후 Redis 카운터 감소
		String redisKey = RedisKeys.reservationSlotReserved(slotId);
		stringRedisTemplate.opsForValue().decrement(redisKey);

		return true;
	}
}