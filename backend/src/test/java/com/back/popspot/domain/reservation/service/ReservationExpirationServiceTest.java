package com.back.popspot.domain.reservation.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.redis.RedisKeys;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationServiceTest {

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private ReservationSlotRepository reservationSlotRepository;

	@Mock
	private RedisTemplate<String, Long> redisTemplate;

	@Mock
	private ValueOperations<String, Long> valueOperations;

	@Test
	@DisplayName("만료된 HELD 예약은 EXPIRED로 변경하고 슬롯 정원을 복구한다")
	void expireExpiredReservations_success() {
		// given
		ReservationExpirationService service = new ReservationExpirationService(
			reservationRepository,
			reservationSlotRepository,
			redisTemplate
		);
		Reservation reservation = createReservation(100L, 1L);

		when(reservationRepository.findByStatusAndHeldUntilBefore(eq(ReservationStatus.HELD), any(LocalDateTime.class)))
			.thenReturn(List.of(reservation));
		when(reservationRepository.expireHeldReservation(
			eq(100L),
			eq(ReservationStatus.HELD),
			eq(ReservationStatus.EXPIRED),
			any(LocalDateTime.class)
		)).thenReturn(1);
		when(reservationSlotRepository.decreaseReservedCount(1L)).thenReturn(1);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		// when
		service.expireExpiredReservations();

		// then
		verify(reservationRepository).findByStatusAndHeldUntilBefore(eq(ReservationStatus.HELD), any(LocalDateTime.class));
		verify(reservationRepository).expireHeldReservation(
			eq(100L),
			eq(ReservationStatus.HELD),
			eq(ReservationStatus.EXPIRED),
			any(LocalDateTime.class)
		);
		verify(reservationSlotRepository).decreaseReservedCount(1L);
		// HELD 만료로 점유가 풀렸으므로 remaining 복구
		verify(valueOperations).increment(RedisKeys.reservationSlotRemaining(1L));
	}

	@Test
	@DisplayName("원자적 만료 업데이트에 실패하면 슬롯 정원을 복구하지 않는다")
	void expireIfHeldAndExpired_skipWhenConditionalUpdateFails() {
		// given
		ReservationExpirationService service = new ReservationExpirationService(
			reservationRepository,
			reservationSlotRepository,
			redisTemplate
		);
		Reservation reservation = createReservation(100L, 1L);
		LocalDateTime now = LocalDateTime.now();

		when(reservationRepository.expireHeldReservation(
			100L,
			ReservationStatus.HELD,
			ReservationStatus.EXPIRED,
			now
		)).thenReturn(0);

		// when
		boolean expired = service.expireIfHeldAndExpired(reservation, now);

		// then
		assertFalse(expired);
		verify(reservationSlotRepository, never()).decreaseReservedCount(any(Long.class));
	}

	@Test
	@DisplayName("슬롯 정원 복구에 실패하면 만료 처리를 실패시킨다")
	void expireIfHeldAndExpired_failWhenRestoreCapacityFails() {
		// given
		ReservationExpirationService service = new ReservationExpirationService(
			reservationRepository,
			reservationSlotRepository,
			redisTemplate
		);
		Reservation reservation = createReservation(100L, 1L);
		LocalDateTime now = LocalDateTime.now();

		when(reservationRepository.expireHeldReservation(
			100L,
			ReservationStatus.HELD,
			ReservationStatus.EXPIRED,
			now
		)).thenReturn(1);
		when(reservationSlotRepository.decreaseReservedCount(1L)).thenReturn(0);

		// when & then
		assertThrows(BusinessException.class, () -> service.expireIfHeldAndExpired(reservation, now));
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
