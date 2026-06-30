package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.dto.ReservationCapacityRebuildResult;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationCancelPoolRepository;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.global.redis.RedisKeys;

@ExtendWith(MockitoExtension.class)
class ReservationCapacityRebuildServiceTest {

	private static final Long SLOT_ID = 1L;

	@Mock
	private ReservationSlotRepository reservationSlotRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private ReservationCancelPoolRepository reservationCancelPoolRepository;

	@Mock
	private RedisTemplate<String, Long> redisTemplate;

	@Mock
	private ValueOperations<String, Long> valueOperations;

	@InjectMocks
	private ReservationCapacityRebuildService service;

	@Test
	@DisplayName("Redis 재구축 시 미공개 취소 예약 pendingCount를 차감한다")
	void rebuildSlotRemaining_subtractsPendingCancelCount() {
		ReservationSlot slot = slot(SLOT_ID, 10, LocalDateTime.now().plusDays(2));
		stubCommon(slot, 4L, 2L);
		when(valueOperations.get(RedisKeys.reservationSlotRemaining(SLOT_ID))).thenReturn(7L);

		ReservationCapacityRebuildResult result = service.rebuildSlotRemaining(SLOT_ID);

		// remaining = capacity(10) - active(4) - pending(2) = 4
		assertThat(result.rebuiltRedisRemaining()).isEqualTo(4L);
		verify(valueOperations).set(eq(RedisKeys.reservationSlotRemaining(SLOT_ID)), eq(4L), anyLong(), eq(TimeUnit.SECONDS));
	}

	@Test
	@DisplayName("Redis 재구축 시 예약 가능 시간 안에 재오픈될 pendingCount만 차감한다")
	void rebuildSlotRemaining_subtractsPendingBeforeReservableUntil() {
		LocalDateTime reservationEndAt = LocalDateTime.of(2026, 6, 28, 23, 0);
		ReservationSlot slot = slot(
			SLOT_ID,
			10,
			LocalDateTime.now().plusDays(2),
			LocalDate.of(2026, 6, 29),
			LocalTime.of(10, 0),
			reservationEndAt
		);
		stubCommon(slot, 4L, 2L);

		service.rebuildSlotRemaining(SLOT_ID);

		verify(reservationCancelPoolRepository).sumScheduledPendingCountBySlotIdAndReopenAtBefore(
			SLOT_ID,
			reservationEndAt
		);
	}

	@Test
	@DisplayName("closeDate가 미래면 Redis 키에 TTL을 함께 설정한다")
	void rebuildSlotRemaining_setsTtl_whenCloseDateInFuture() {
		ReservationSlot slot = slot(SLOT_ID, 10, LocalDateTime.now().plusDays(2));
		stubCommon(slot, 3L, 0L);

		service.rebuildSlotRemaining(SLOT_ID);

		// remaining = 10 - 3 - 0 = 7, TTL 포함된 4-arg set 호출
		verify(valueOperations).set(eq(RedisKeys.reservationSlotRemaining(SLOT_ID)), eq(7L), anyLong(), eq(TimeUnit.SECONDS));
		// TTL 없는 2-arg set은 호출되지 않아야 한다
		verify(valueOperations, never()).set(eq(RedisKeys.reservationSlotRemaining(SLOT_ID)), anyLong());
	}

	@Test
	@DisplayName("TTL 값은 closeDate까지 남은 초로 설정된다")
	void rebuildSlotRemaining_ttlMatchesCloseDate() {
		LocalDateTime closeDate = LocalDateTime.now().plusHours(3);
		ReservationSlot slot = slot(SLOT_ID, 10, closeDate);
		stubCommon(slot, 0L, 0L);

		// now()가 메서드 내부에서 호출되므로 호출 전/후로 기대 TTL 범위를 잡는다(시간 흐르며 줄어듦).
		long ttlUpperBound = ChronoUnit.SECONDS.between(LocalDateTime.now(), closeDate);
		service.rebuildSlotRemaining(SLOT_ID);
		long ttlLowerBound = ChronoUnit.SECONDS.between(LocalDateTime.now(), closeDate);

		ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
		verify(valueOperations).set(
			eq(RedisKeys.reservationSlotRemaining(SLOT_ID)), eq(10L), ttlCaptor.capture(), eq(TimeUnit.SECONDS));
		assertThat(ttlCaptor.getValue()).isBetween(ttlLowerBound, ttlUpperBound);
	}

	@Test
	@DisplayName("closeDate가 이미 지났으면 TTL 없이 SET한다")
	void rebuildSlotRemaining_setsWithoutTtl_whenCloseDatePassed() {
		ReservationSlot slot = slot(SLOT_ID, 10, LocalDateTime.now().minusMinutes(1));
		stubCommon(slot, 2L, 0L);

		service.rebuildSlotRemaining(SLOT_ID);

		// remaining = 10 - 2 - 0 = 8, TTL 없는 2-arg set만 호출
		verify(valueOperations).set(RedisKeys.reservationSlotRemaining(SLOT_ID), 8L);
		verify(valueOperations, never()).set(any(), anyLong(), anyLong(), any(TimeUnit.class));
	}

	@Test
	@DisplayName("슬롯 조회는 findByIdWithPopupStore로 한다 (findById가 아님)")
	void rebuildSlotRemaining_usesFindByIdWithPopupStore() {
		ReservationSlot slot = slot(SLOT_ID, 10, LocalDateTime.now().plusDays(2));
		stubCommon(slot, 1L, 0L);

		service.rebuildSlotRemaining(SLOT_ID);

		verify(reservationSlotRepository).findByIdWithPopupStore(SLOT_ID);
		verify(reservationSlotRepository, never()).findById(any());
	}

	// findByIdWithPopupStore / count / pending / opsForValue 를 한 번에 스텁한다.
	private void stubCommon(ReservationSlot slot, long activeCount, long pendingCount) {
		when(reservationSlotRepository.findByIdWithPopupStore(SLOT_ID)).thenReturn(Optional.of(slot));
		when(reservationRepository.countBySlotIdAndStatusIn(
			eq(SLOT_ID), eq(List.of(ReservationStatus.HELD, ReservationStatus.CONFIRMED)))).thenReturn(activeCount);
		when(reservationCancelPoolRepository.sumScheduledPendingCountBySlotIdAndReopenAtBefore(
			eq(SLOT_ID), any(LocalDateTime.class))).thenReturn(pendingCount);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	private ReservationSlot slot(Long id, int capacity, LocalDateTime closeDate) {
		return slot(
			id,
			capacity,
			closeDate,
			LocalDate.of(2026, 6, 29),
			LocalTime.of(10, 0),
			LocalDateTime.of(2026, 6, 28, 23, 0)
		);
	}

	private ReservationSlot slot(
		Long id,
		int capacity,
		LocalDateTime closeDate,
		LocalDate slotDate,
		LocalTime startTime,
		LocalDateTime reservationEndAt
	) {
		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "closeDate", closeDate);
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", reservationEndAt);

		ReservationSlot slot = new ReservationSlot();
		ReflectionTestUtils.setField(slot, "id", id);
		ReflectionTestUtils.setField(slot, "capacity", capacity);
		ReflectionTestUtils.setField(slot, "popupStore", popupStore);
		ReflectionTestUtils.setField(slot, "slotDate", slotDate);
		ReflectionTestUtils.setField(slot, "startTime", startTime);
		return slot;
	}
}
