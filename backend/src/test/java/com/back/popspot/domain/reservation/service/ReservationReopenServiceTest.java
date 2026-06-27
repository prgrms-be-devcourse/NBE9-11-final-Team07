package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
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
	private RedisTemplate<String, Long> redisTemplate;

	@Mock
	private ValueOperations<String, Long> valueOperations;

	@InjectMocks
	private ReservationReopenService service;

	@Test
	@DisplayName("재오픈 성공 시 pendingCount만큼 Redis remaining이 증가하고 pool은 OPENED 처리된다")
	void reopenDuePools_success() {
		ReservationCancelPool pool = pool(10L, 1L, 2);
		when(reservationCancelPoolRepository.findByReopenStatusAndReopenAtLessThanEqualAndPendingCountGreaterThan(
			org.mockito.ArgumentMatchers.eq(ReservationCancelPoolStatus.SCHEDULED),
			org.mockito.ArgumentMatchers.any(LocalDateTime.class),
			org.mockito.ArgumentMatchers.eq(0)
		)).thenReturn(List.of(pool));
		when(reservationCancelPoolRepository.claimOpening(
			10L,
			ReservationCancelPoolStatus.SCHEDULED,
			ReservationCancelPoolStatus.OPENING
		)).thenReturn(1);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		service.reopenDuePools();

		verify(valueOperations).increment(RedisKeys.reservationSlotRemaining(1L), 2L);
		assertThat(pool.getPendingCount()).isZero();
		assertThat(pool.getOpenedCount()).isEqualTo(2);
		assertThat(pool.getReopenStatus()).isEqualTo(ReservationCancelPoolStatus.OPENED);
	}

	@Test
	@DisplayName("SCHEDULED에서 OPENING 조건부 변경 실패 시 Redis remaining은 증가하지 않는다")
	void reopenDuePools_skipWhenClaimFails() {
		ReservationCancelPool pool = pool(10L, 1L, 2);
		when(reservationCancelPoolRepository.findByReopenStatusAndReopenAtLessThanEqualAndPendingCountGreaterThan(
			org.mockito.ArgumentMatchers.eq(ReservationCancelPoolStatus.SCHEDULED),
			org.mockito.ArgumentMatchers.any(LocalDateTime.class),
			org.mockito.ArgumentMatchers.eq(0)
		)).thenReturn(List.of(pool));
		when(reservationCancelPoolRepository.claimOpening(
			10L,
			ReservationCancelPoolStatus.SCHEDULED,
			ReservationCancelPoolStatus.OPENING
		)).thenReturn(0);

		service.reopenDuePools();

		verify(redisTemplate, never()).opsForValue();
		assertThat(pool.getPendingCount()).isEqualTo(2);
		assertThat(pool.getReopenStatus()).isEqualTo(ReservationCancelPoolStatus.SCHEDULED);
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
