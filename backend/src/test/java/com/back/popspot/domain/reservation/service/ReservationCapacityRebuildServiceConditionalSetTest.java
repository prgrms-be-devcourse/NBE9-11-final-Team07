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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * C단계 — {@link ReservationCapacityRebuildService#rebuildSlotRemaining(Long)} 의 조건부 SET 규칙 검증.
 *
 * <p>기존 {@link ReservationCapacityRebuildServiceTest} 는 수정하지 않고, 조건부 규칙 케이스만 여기서 다룬다.
 *
 * <p>규칙:
 * <ul>
 *   <li>현재 Redis 값 null → SET</li>
 *   <li>계산값 &gt; 현재값 → skip(로그, 예외 없음)</li>
 *   <li>계산값 &le; 현재값 → SET</li>
 * </ul>
 * TTL 있음(4-arg set) / 없음(2-arg set) 두 경로 모두 동일하게.
 */
@ExtendWith(MockitoExtension.class)
class ReservationCapacityRebuildServiceConditionalSetTest {

	private static final Long SLOT_ID = 1L;
	private static final String KEY = RedisKeys.reservationSlotRemaining(SLOT_ID);

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

	private Logger serviceLogger;
	private ListAppender<ILoggingEvent> logCaptor;

	@BeforeEach
	void setUpLogCaptor() {
		serviceLogger = (Logger) LoggerFactory.getLogger(ReservationCapacityRebuildService.class);
		logCaptor = new ListAppender<>();
		logCaptor.start();
		serviceLogger.addAppender(logCaptor);
	}

	@AfterEach
	void tearDownLogCaptor() {
		serviceLogger.detachAppender(logCaptor);
	}

	// ── 현재값 null → SET ────────────────────────────────────────────────────

	@Test
	@DisplayName("현재 Redis 값이 null이면 TTL 경로로 SET한다")
	void previousNull_ttlPath_sets() {
		givenSlot(10, /*ttl*/ true);
		givenCounts(3, 0);            // remaining = 7
		when(valueOperations.get(KEY)).thenReturn(null);

		ReservationCapacityRebuildResult result = service.rebuildSlotRemaining(SLOT_ID);

		verify(valueOperations).set(eq(KEY), eq(7L), anyLong(), eq(TimeUnit.SECONDS));
		assertThat(result.rebuiltRedisRemaining()).isEqualTo(7L);
	}

	@Test
	@DisplayName("현재 Redis 값이 null이면 TTL 없는 경로로도 SET한다")
	void previousNull_noTtlPath_sets() {
		givenSlot(10, /*ttl*/ false);
		givenCounts(3, 0);            // remaining = 7
		when(valueOperations.get(KEY)).thenReturn(null);

		service.rebuildSlotRemaining(SLOT_ID);

		verify(valueOperations).set(KEY, 7L);
	}

	// ── 계산값 > 현재값 → skip ───────────────────────────────────────────────

	@Test
	@DisplayName("계산값이 현재값보다 크면 TTL 경로에서 SET하지 않고 skip한다 (skip 로그)")
	void calcGreaterThanPrevious_ttlPath_skips() {
		givenSlot(10, /*ttl*/ true);
		givenCounts(3, 0);            // remaining = 7
		when(valueOperations.get(KEY)).thenReturn(4L);   // 7 > 4 → 초과판매 위험 → skip

		ReservationCapacityRebuildResult result = service.rebuildSlotRemaining(SLOT_ID);

		verify(valueOperations, never()).set(any(), anyLong(), anyLong(), any(TimeUnit.class));
		verify(valueOperations, never()).set(any(), anyLong());
		// skip 시 결과는 현재값을 유지한 것으로 반환한다.
		assertThat(result.previousRedisRemaining()).isEqualTo(4L);
		assertThat(result.rebuiltRedisRemaining()).isEqualTo(4L);
		assertThat(logCaptor.list)
			.anyMatch(e -> e.getLevel() == Level.WARN
				&& e.getFormattedMessage().contains("RESERVATION_REDIS_REBUILD_SKIPPED"));
	}

	@Test
	@DisplayName("계산값이 현재값보다 크면 TTL 없는 경로에서도 SET하지 않고 skip한다")
	void calcGreaterThanPrevious_noTtlPath_skips() {
		givenSlot(10, /*ttl*/ false);
		givenCounts(3, 0);            // remaining = 7
		when(valueOperations.get(KEY)).thenReturn(4L);

		service.rebuildSlotRemaining(SLOT_ID);

		verify(valueOperations, never()).set(any(), anyLong());
		verify(valueOperations, never()).set(any(), anyLong(), anyLong(), any(TimeUnit.class));
		assertThat(logCaptor.list)
			.anyMatch(e -> e.getLevel() == Level.WARN
				&& e.getFormattedMessage().contains("RESERVATION_REDIS_REBUILD_SKIPPED"));
	}

	// ── 계산값 == 현재값 → SET ──────────────────────────────────────────────

	@Test
	@DisplayName("계산값이 현재값과 같으면 TTL 경로로 SET한다")
	void calcEqualsPrevious_ttlPath_sets() {
		givenSlot(10, /*ttl*/ true);
		givenCounts(3, 0);            // remaining = 7
		when(valueOperations.get(KEY)).thenReturn(7L);

		service.rebuildSlotRemaining(SLOT_ID);

		verify(valueOperations).set(eq(KEY), eq(7L), anyLong(), eq(TimeUnit.SECONDS));
	}

	// ── 계산값 < 현재값 → SET (하향 교정) ───────────────────────────────────

	@Test
	@DisplayName("계산값이 현재값보다 작으면 TTL 경로로 SET한다 (하향 교정)")
	void calcLessThanPrevious_ttlPath_sets() {
		givenSlot(10, /*ttl*/ true);
		givenCounts(4, 2);           // remaining = 4
		when(valueOperations.get(KEY)).thenReturn(99L);

		ReservationCapacityRebuildResult result = service.rebuildSlotRemaining(SLOT_ID);

		verify(valueOperations).set(eq(KEY), eq(4L), anyLong(), eq(TimeUnit.SECONDS));
		assertThat(result.rebuiltRedisRemaining()).isEqualTo(4L);
	}

	@Test
	@DisplayName("계산값이 현재값보다 작으면 TTL 없는 경로로도 SET한다 (하향 교정)")
	void calcLessThanPrevious_noTtlPath_sets() {
		givenSlot(10, /*ttl*/ false);
		givenCounts(4, 2);           // remaining = 4
		when(valueOperations.get(KEY)).thenReturn(99L);

		service.rebuildSlotRemaining(SLOT_ID);

		verify(valueOperations).set(KEY, 4L);
	}

	// ── helpers ─────────────────────────────────────────────────────────────

	private void givenCounts(long activeCount, long pendingCount) {
		when(reservationRepository.countBySlotIdAndStatusIn(
			eq(SLOT_ID), eq(List.of(ReservationStatus.HELD, ReservationStatus.CONFIRMED)))).thenReturn(activeCount);
		when(reservationCancelPoolRepository.sumScheduledPendingCountBySlotIdAndReopenAtBefore(
			eq(SLOT_ID), any(LocalDateTime.class))).thenReturn(pendingCount);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	private void givenSlot(int capacity, boolean ttlInFuture) {
		LocalDateTime closeDate = ttlInFuture
			? LocalDateTime.now().plusDays(2)
			: LocalDateTime.now().minusMinutes(1);

		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "closeDate", closeDate);
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", LocalDateTime.of(2026, 6, 28, 23, 0));

		ReservationSlot slot = new ReservationSlot();
		ReflectionTestUtils.setField(slot, "id", SLOT_ID);
		ReflectionTestUtils.setField(slot, "capacity", capacity);
		ReflectionTestUtils.setField(slot, "popupStore", popupStore);
		ReflectionTestUtils.setField(slot, "slotDate", LocalDate.of(2026, 6, 29));
		ReflectionTestUtils.setField(slot, "startTime", LocalTime.of(10, 0));

		when(reservationSlotRepository.findByIdWithPopupStore(SLOT_ID)).thenReturn(Optional.of(slot));
	}
}
