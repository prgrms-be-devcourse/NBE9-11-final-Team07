package com.back.popspot.domain.reservation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

/**
 * DB 쓰기 전용 빈의 단위 테스트.
 *
 * <p>{@link ReservationService}/{@link ReservationExpirationService} 에서 분리되어 이 빈으로 옮겨온
 * "조건부 상태 변경 + 슬롯 정원 복구" 분기 로직을 검증한다.
 * (트랜잭션 전파/커밋 타이밍은 단위 테스트 범위 밖 — 통합 테스트에서 검증)
 */
@ExtendWith(MockitoExtension.class)
class ReservationCommandServiceTest {

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private ReservationCancelPoolService reservationCancelPoolService;

	@InjectMocks
	private ReservationCommandService service;

	// ── expireInTx ─────────────────────────────────────────────────────────────
	@Test
	@DisplayName("HELD 만료 업데이트가 0건이면 false 를 반환하고 슬롯 정원을 건드리지 않는다")
	void expireInTx_returnsFalseWhenNoRowUpdated() {
		LocalDateTime now = LocalDateTime.now();
		when(reservationRepository.expireHeldReservation(
			100L, ReservationStatus.HELD, ReservationStatus.EXPIRED, now)).thenReturn(0);

		boolean expired = service.expireInTx(100L, 1L, now);

		assertFalse(expired);
	}

	@Test
	@DisplayName("만료 상태 변경이 성공하면 true 를 반환한다")
	void expireInTx_returnsTrueOnSuccess() {
		LocalDateTime now = LocalDateTime.now();
		when(reservationRepository.expireHeldReservation(
			100L, ReservationStatus.HELD, ReservationStatus.EXPIRED, now)).thenReturn(1);

		assertTrue(service.expireInTx(100L, 1L, now));
	}

	// ── cancelInTx ─────────────────────────────────────────────────────────────
	@Test
	@DisplayName("CONFIRMED 취소가 0건이면 예외로 롤백시키고 슬롯 정원을 건드리지 않는다")
	void cancelInTx_throwsWhenNoConfirmedReservation() {
		LocalDateTime now = LocalDateTime.now();
		when(reservationRepository.cancelConfirmedReservation(
			100L, ReservationStatus.CONFIRMED, ReservationStatus.CANCELED, now)).thenReturn(0);

		BusinessException exception = assertThrows(BusinessException.class,
			() -> service.cancelInTx(100L, 1L, now));

		assertTrue(exception.getErrorCode() == ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED_STATUS);
		verify(reservationCancelPoolService, never()).accrue(1L, now);
	}

	@Test
	@DisplayName("취소 상태 변경이 성공하면 취소 예약 재오픈 pool에 적립한다")
	void cancelInTx_success() {
		LocalDateTime now = LocalDateTime.now();
		when(reservationRepository.cancelConfirmedReservation(
			100L, ReservationStatus.CONFIRMED, ReservationStatus.CANCELED, now)).thenReturn(1);

		assertDoesNotThrow(() -> service.cancelInTx(100L, 1L, now));
		verify(reservationCancelPoolService).accrue(1L, now);
	}
}
