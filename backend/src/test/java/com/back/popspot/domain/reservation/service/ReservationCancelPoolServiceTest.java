package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.reservation.entity.ReservationCancelPool;
import com.back.popspot.domain.reservation.entity.ReservationCancelPoolStatus;
import com.back.popspot.domain.reservation.repository.ReservationCancelPoolRepository;

@ExtendWith(MockitoExtension.class)
class ReservationCancelPoolServiceTest {

	@Mock
	private ReservationCancelPoolRepository reservationCancelPoolRepository;

	@Mock
	private ReservationReopenPolicy reservationReopenPolicy;

	@InjectMocks
	private ReservationCancelPoolService service;

	@Test
	@DisplayName("취소 예약 적립 시 재오픈 시각을 계산해 pendingCount 원자 증가 쿼리를 호출한다")
	void accrue_increasesPendingCountAtomically() {
		LocalDateTime canceledAt = LocalDateTime.of(2026, 6, 27, 18, 0);
		LocalDateTime reopenAt = LocalDateTime.of(2026, 6, 27, 19, 0);

		when(reservationReopenPolicy.calculateReopenAt(canceledAt)).thenReturn(reopenAt);
		when(reservationCancelPoolRepository.accruePendingCount(1L, reopenAt)).thenReturn(1);

		service.accrue(1L, canceledAt);

		verify(reservationCancelPoolRepository).accruePendingCount(1L, reopenAt);
		assertThat(reopenAt).isEqualTo(LocalDateTime.of(2026, 6, 27, 19, 0));
	}

	@Test
	@DisplayName("재오픈 claim은 SCHEDULED 상태 풀만 OPENING으로 조건부 변경한다")
	void claimOpeningInTx_updatesScheduledPoolOnly() {
		when(reservationCancelPoolRepository.claimOpening(
			1L,
			ReservationCancelPoolStatus.SCHEDULED,
			ReservationCancelPoolStatus.OPENING
		)).thenReturn(1);

		boolean claimed = service.claimOpeningInTx(1L);

		assertThat(claimed).isTrue();
		verify(reservationCancelPoolRepository).claimOpening(
			1L,
			ReservationCancelPoolStatus.SCHEDULED,
			ReservationCancelPoolStatus.OPENING
		);
	}

	@Test
	@DisplayName("OPENING 상태 풀은 pendingCount를 openedCount로 옮기고 OPENED 처리한다")
	void markOpenedInTx_openingPool_marksOpened() {
		ReservationCancelPool pool = pool(1L, 2);
		ReflectionTestUtils.setField(pool, "reopenStatus", ReservationCancelPoolStatus.OPENING);
		when(reservationCancelPoolRepository.findById(1L)).thenReturn(Optional.of(pool));

		boolean opened = service.markOpenedInTx(1L);

		assertThat(opened).isTrue();
		assertThat(pool.getPendingCount()).isZero();
		assertThat(pool.getOpenedCount()).isEqualTo(2);
		assertThat(pool.getReopenStatus()).isEqualTo(ReservationCancelPoolStatus.OPENED);
	}

	@Test
	@DisplayName("OPENING 상태가 아니면 OPENED 처리하지 않고 false를 반환한다")
	void markOpenedInTx_notOpening_returnsFalse() {
		ReservationCancelPool pool = pool(1L, 2);
		when(reservationCancelPoolRepository.findById(1L)).thenReturn(Optional.of(pool));

		boolean opened = service.markOpenedInTx(1L);

		assertThat(opened).isFalse();
		assertThat(pool.getPendingCount()).isEqualTo(2);
		assertThat(pool.getOpenedCount()).isZero();
		assertThat(pool.getReopenStatus()).isEqualTo(ReservationCancelPoolStatus.SCHEDULED);
	}

	private ReservationCancelPool pool(Long poolId, int pendingCount) {
		ReservationSlot slot = new ReservationSlot();
		ReflectionTestUtils.setField(slot, "id", 1L);
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
