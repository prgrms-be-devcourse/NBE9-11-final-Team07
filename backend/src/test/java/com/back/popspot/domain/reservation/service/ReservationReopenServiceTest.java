package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import com.back.popspot.domain.reservation.entity.ReservationCancelPool;
import com.back.popspot.domain.reservation.entity.ReservationCancelPoolStatus;
import com.back.popspot.domain.reservation.repository.ReservationCancelPoolRepository;
import com.back.popspot.global.redis.RedisKeys;

@ExtendWith(MockitoExtension.class)
class ReservationReopenServiceTest {

	@Mock
	private ReservationCancelPoolRepository reservationCancelPoolRepository;

	@Mock
	private ReservationCancelPoolService reservationCancelPoolService;

	@Mock
	private RedisTemplate<String, Long> redisTemplate;

	@Mock
	private ValueOperations<String, Long> valueOperations;

	@InjectMocks
	private ReservationReopenService service;

	@Test
	@DisplayName("재오픈 성공 시 pendingCount만큼 Redis remaining이 증가하고 pool은 OPENED 처리된다")
	void reopenDuePools_success() {
		ReservationCancelPool pool = pool(10L, 1L, 2);
		when(reservationCancelPoolRepository.findDuePoolsWithSlot(
			eq(ReservationCancelPoolStatus.SCHEDULED),
			any(LocalDateTime.class),
			eq(0)
		)).thenReturn(List.of(pool));
		when(reservationCancelPoolService.claimOpeningInTx(10L)).thenReturn(true);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(reservationCancelPoolService.markOpenedInTx(10L)).thenReturn(true);

		service.reopenDuePools();

		verify(valueOperations).increment(RedisKeys.reservationSlotRemaining(1L), 2L);
		verify(reservationCancelPoolService).markOpenedInTx(10L);
	}

	@Test
	@DisplayName("SCHEDULED에서 OPENING 조건부 변경 실패 시 Redis remaining은 증가하지 않는다")
	void reopenDuePools_skipWhenClaimFails() {
		ReservationCancelPool pool = pool(10L, 1L, 2);
		when(reservationCancelPoolRepository.findDuePoolsWithSlot(
			eq(ReservationCancelPoolStatus.SCHEDULED),
			any(LocalDateTime.class),
			eq(0)
		)).thenReturn(List.of(pool));
		when(reservationCancelPoolService.claimOpeningInTx(10L)).thenReturn(false);

		service.reopenDuePools();

		verify(redisTemplate, never()).opsForValue();
		verify(reservationCancelPoolService, never()).markOpenedInTx(10L);
		assertThat(pool.getPendingCount()).isEqualTo(2);
		assertThat(pool.getReopenStatus()).isEqualTo(ReservationCancelPoolStatus.SCHEDULED);
	}

	@Test
	@DisplayName("Redis INCR 예외 시 OPENED 처리하지 않고 다음 처리를 중단한다")
	void reopenDuePools_redisFailure_doesNotMarkOpened() {
		ReservationCancelPool pool = pool(10L, 1L, 2);
		when(reservationCancelPoolRepository.findDuePoolsWithSlot(
			eq(ReservationCancelPoolStatus.SCHEDULED),
			any(LocalDateTime.class),
			eq(0)
		)).thenReturn(List.of(pool));
		when(reservationCancelPoolService.claimOpeningInTx(10L)).thenAnswer(invocation -> {
			ReflectionTestUtils.setField(pool, "reopenStatus", ReservationCancelPoolStatus.OPENING);
			return true;
		});
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		doThrow(new RuntimeException("redis timeout"))
			.when(valueOperations).increment(RedisKeys.reservationSlotRemaining(1L), 2L);

		service.reopenDuePools();

		verify(reservationCancelPoolService, never()).markOpenedInTx(10L);
		assertThat(pool.getPendingCount()).isEqualTo(2);
		assertThat(pool.getReopenStatus()).isEqualTo(ReservationCancelPoolStatus.OPENING);
	}

	private ReservationCancelPool pool(Long poolId, Long slotId, int pendingCount) {
		ReservationSlot slot = new ReservationSlot();
		ReflectionTestUtils.setField(slot, "id", slotId);
		ReflectionTestUtils.setField(slot, "slotDate", LocalDate.of(2026, 6, 28));
		ReflectionTestUtils.setField(slot, "startTime", LocalTime.of(10, 0));

		ReservationCancelPool pool = ReservationCancelPool.create(slot, LocalDateTime.of(2026, 6, 27, 19, 0));
		ReflectionTestUtils.setField(pool, "id", poolId);
		for (int i = 0; i < pendingCount; i++) {
			pool.increasePending();
		}
		return pool;
	}
}
