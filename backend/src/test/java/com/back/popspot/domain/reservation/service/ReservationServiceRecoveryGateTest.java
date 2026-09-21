package com.back.popspot.domain.reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.payment.service.PaymentReadyService;
import com.back.popspot.domain.payment.service.PaymentService;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.dto.request.ReservationCreateRequest;
import com.back.popspot.domain.reservation.dto.response.ReservationCreateResponse;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;

/**
 * B단계 — createReservation 의 DECR 직전 복구 게이트 체크 단위 테스트.
 *
 * <p>기존 {@link ReservationServiceTest} 는 수정하지 않고, 게이트 관련 케이스만 여기서 다룬다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationService 복구 게이트 (DECR 직전 차단) 단위 테스트")
class ReservationServiceRecoveryGateTest {

	@Mock
	private ReservationRepository reservationRepository;
	@Mock
	private ReservationSlotRepository reservationSlotRepository;
	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private PaymentService paymentService;
	@Mock
	private UserRepository userRepository;
	@Mock
	private ReservationExpirationService reservationExpirationService;
	@Mock
	private ReservationCommandService reservationCommandService;
	@Mock
	private ReservationWaitlistService reservationWaitlistService;
	@Mock
	private ReservationRedisService reservationRedisService;
	@Mock
	private WaitingQueueRedisService waitingQueueRedisService;
	@Mock
	private PaymentReadyService paymentReadyService;

	private ReservationService reservationService;

	private ReservationService service() {
		if (reservationService == null) {
			reservationService = new ReservationService(
				reservationRepository, reservationSlotRepository, paymentRepository, paymentService,
				userRepository, reservationExpirationService, reservationCommandService,
				reservationRedisService, waitingQueueRedisService, paymentReadyService, reservationWaitlistService
			);
		}
		return reservationService;
	}

	@Test
	@DisplayName("복구 게이트 ON → 모든 검증 통과해도 DECR로 진입하지 않고 RESERVATION_RECOVERY_IN_PROGRESS로 거절한다")
	void createReservation_blockedWhenRecovering() {
		stubValidationPasses();
		when(reservationRedisService.isRecovering()).thenReturn(true);

		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> service().createReservation(new ReservationCreateRequest(1L), 2L)
		);

		assertEquals(ErrorCode.RESERVATION_RECOVERY_IN_PROGRESS, exception.getErrorCode());
		verify(reservationRedisService, never()).decrement(any());
		verify(reservationRedisService, never()).increment(any());
		verify(reservationCommandService, never()).save(any(), any(), any(), any());
		verify(waitingQueueRedisService, never()).revokeProceedPermission(any(Long.class), any(String.class));
	}

	@Test
	@DisplayName("복구 게이트 OFF → 기존과 동일하게 DECR 이후 정상 선점 흐름을 탄다")
	void createReservation_proceedsWhenNotRecovering() {
		stubValidationPasses();
		when(reservationRedisService.isRecovering()).thenReturn(false);
		when(reservationRedisService.decrement(RedisKeys.reservationSlotRemaining(1L))).thenReturn(9L);
		when(reservationCommandService.save(any(User.class), any(ReservationSlot.class),
			any(LocalDateTime.class), any(LocalDateTime.class)))
			.thenAnswer(invocation -> {
				Reservation reservation = Reservation.createHeld(
					invocation.getArgument(0), invocation.getArgument(1),
					invocation.getArgument(2), invocation.getArgument(3));
				ReflectionTestUtils.setField(reservation, "id", 100L);
				return reservation;
			});

		ReservationCreateResponse response = service().createReservation(new ReservationCreateRequest(1L), 2L);

		assertEquals(100L, response.reservationId());
		assertEquals(ReservationStatus.HELD, response.status());
		verify(reservationRedisService).decrement(RedisKeys.reservationSlotRemaining(1L));
		verify(reservationCommandService).save(any(), any(), any(), any());
	}

	/** findByIdWithPopupStore / hasProceedPermission / user / 중복예약 없음 까지 — 게이트 체크 직전 지점 통과 스텁 */
	private void stubValidationPasses() {
		PopupStore popupStore = new PopupStore();
		LocalDateTime now = LocalDateTime.now();
		ReflectionTestUtils.setField(popupStore, "id", 1L);
		ReflectionTestUtils.setField(popupStore, "reservationStartAt", now.minusDays(1));
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", now.plusDays(1));
		ReflectionTestUtils.setField(popupStore, "feeType", PopupFeeType.FREE);

		ReservationSlot slot = new ReservationSlot();
		ReflectionTestUtils.setField(slot, "id", 1L);
		ReflectionTestUtils.setField(slot, "popupStore", popupStore);
		ReflectionTestUtils.setField(slot, "slotDate", LocalDate.now().plusDays(1));
		ReflectionTestUtils.setField(slot, "startTime", LocalTime.of(10, 0));
		ReflectionTestUtils.setField(slot, "capacity", 10);
		ReflectionTestUtils.setField(slot, "reservedCount", 0);

		User user = User.create("user@test.com", "user");
		ReflectionTestUtils.setField(user, "id", 2L);

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.of(slot));
		when(waitingQueueRedisService.hasProceedPermission(1L, "2")).thenReturn(true);
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.findByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull(2L, 1L))
			.thenReturn(Optional.empty());
	}
}
