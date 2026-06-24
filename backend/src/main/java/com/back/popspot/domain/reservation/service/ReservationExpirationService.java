package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationExpirationService {

	private final ReservationRepository reservationRepository;
	private final RedisTemplate<String, Long> redisTemplate;
	private final ReservationCommandService reservationCommandService;  // ← 주입 추가

	// @Transactional 제거 ← 루프는 트랜잭션 경계가 아님 (건별로 따로 커밋되게)
	public void expireExpiredReservations() {
		LocalDateTime now = LocalDateTime.now();
		List<Reservation> reservations = reservationRepository.findByStatusAndHeldUntilBefore(
			ReservationStatus.HELD,
			now
		);

		for (Reservation reservation : reservations) {
			expireOne(reservation, now);
		}
	}

	// @Transactional 제거 ← DB는 expireInTx가, Redis는 커밋 후 여기서
	public void expireOne(Reservation reservation, LocalDateTime now) {
		Long slotId = reservation.getSlot().getId();

		// DB 만료는 별도 빈 트랜잭션에 위임 (건별 커밋)
		boolean expired = reservationCommandService.expireInTx(
			reservation.getId(), slotId, now);

		// 커밋 성공(true)일 때만 Redis 복구
		if (expired) {
			redisTemplate.opsForValue()
				.increment(RedisKeys.reservationSlotRemaining(slotId));
		}
	}
}
