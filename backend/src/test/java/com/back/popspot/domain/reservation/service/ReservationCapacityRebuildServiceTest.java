package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

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
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.dto.ReservationCapacityRebuildResult;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationCancelPoolRepository;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.global.redis.RedisKeys;

@ExtendWith(MockitoExtension.class)
class ReservationCapacityRebuildServiceTest {

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
		ReservationSlot slot = new ReservationSlot();
		ReflectionTestUtils.setField(slot, "id", 1L);
		ReflectionTestUtils.setField(slot, "capacity", 10);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
		when(reservationRepository.countBySlotIdAndStatusIn(
			org.mockito.ArgumentMatchers.eq(1L),
			org.mockito.ArgumentMatchers.eq(java.util.List.of(ReservationStatus.HELD, ReservationStatus.CONFIRMED))
		)).thenReturn(4L);
		when(reservationCancelPoolRepository.sumPendingCountBySlotId(1L)).thenReturn(2L);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(RedisKeys.reservationSlotRemaining(1L))).thenReturn(7L);

		ReservationCapacityRebuildResult result = service.rebuildSlotRemaining(1L);

		assertThat(result.rebuiltRedisRemaining()).isEqualTo(4L);
		org.mockito.Mockito.verify(valueOperations).set(RedisKeys.reservationSlotRemaining(1L), 4L);
	}
}
