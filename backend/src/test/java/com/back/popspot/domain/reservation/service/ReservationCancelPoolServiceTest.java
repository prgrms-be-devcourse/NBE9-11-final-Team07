package com.back.popspot.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
