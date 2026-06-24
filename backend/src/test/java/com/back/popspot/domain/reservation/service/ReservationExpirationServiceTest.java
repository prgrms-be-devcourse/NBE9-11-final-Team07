package com.back.popspot.domain.reservation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.global.redis.RedisKeys;

/**
 * 만료 빈의 단위 테스트.
 *
 * <p>DB 만료(EXPIRED 전환 + 슬롯 정원 복구)는 {@link ReservationCommandService#expireInTx} 로 위임됐다.
 * 이 빈의 책임은 "커밋 결과(expireInTx 반환값)에 따라 Redis remaining 을 복구할지" 분기하는 것뿐이므로,
 * 여기서는 그 분기만 검증한다. DB 분기는 {@link ReservationCommandServiceTest} 에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationExpirationServiceTest {

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private RedisTemplate<String, Long> redisTemplate;

	@Mock
	private ReservationCommandService reservationCommandService;

	@Mock
	private ValueOperations<String, Long> valueOperations;

	@InjectMocks
	private ReservationExpirationService service;

	@Test
	@DisplayName("DB 만료 커밋(expireInTx=true)에 성공하면 슬롯 정원을 Redis 에 복구한다")
	void expireExpiredReservations_restoresRedisWhenCommitted() {
		// given
		Reservation reservation = createReservation(100L, 1L);

		when(reservationRepository.findByStatusAndHeldUntilBefore(eq(ReservationStatus.HELD), any(LocalDateTime.class)))
			.thenReturn(List.of(reservation));
		when(reservationCommandService.expireInTx(eq(100L), eq(1L), any(LocalDateTime.class)))
			.thenReturn(true);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		// when
		service.expireExpiredReservations();

		// then
		verify(reservationCommandService).expireInTx(eq(100L), eq(1L), any(LocalDateTime.class));
		// DB 커밋 성공 → remaining 복구
		verify(valueOperations).increment(RedisKeys.reservationSlotRemaining(1L));
	}

	@Test
	@DisplayName("DB 만료가 적용되지 않으면(expireInTx=false) Redis 를 건드리지 않는다")
	void expireOne_skipRedisWhenNotExpired() {
		// given
		Reservation reservation = createReservation(100L, 1L);
		LocalDateTime now = LocalDateTime.now();

		when(reservationCommandService.expireInTx(100L, 1L, now)).thenReturn(false);

		// when
		service.expireOne(reservation, now);

		// then
		verify(redisTemplate, never()).opsForValue();
	}

	private Reservation createReservation(Long reservationId, Long slotId) {
		ReservationSlot slot = new ReservationSlot();
		ReflectionTestUtils.setField(slot, "id", slotId);

		Reservation reservation = new Reservation();
		ReflectionTestUtils.setField(reservation, "id", reservationId);
		ReflectionTestUtils.setField(reservation, "slot", slot);

		return reservation;
	}
}
