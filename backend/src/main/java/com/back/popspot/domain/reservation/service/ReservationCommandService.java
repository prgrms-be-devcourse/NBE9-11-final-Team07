package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.domain.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * 예약 DB 쓰기 전용 빈.
 *
 * <p>Redis 재고 차감/롤백을 담당하는 {@link ReservationService} 와 분리한다.
 * DB 저장(커밋 단계 포함) 실패를 호출부 try-catch 가 잡아 Redis 롤백을 수행할 수 있도록,
 * 트랜잭션 경계를 이 빈의 메서드 단위로 둔다.
 *
 * <p>같은 클래스 내 self-invocation 으로는 {@code @Transactional} 프록시가 적용되지 않으므로,
 * 반드시 별도 빈으로 분리해 주입받아 호출해야 한다.
 */
@Service
@RequiredArgsConstructor
public class ReservationCommandService {

	private final ReservationRepository reservationRepository;

	@Transactional
	public Reservation save(User user, ReservationSlot slot, LocalDateTime now, LocalDateTime heldUntil) {
		Reservation reservation = Reservation.createHeld(user, slot, now, heldUntil);
		return reservationRepository.save(reservation);
	}
}
