package com.back.popspot.domain.reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.payment.service.PaymentReadyService;
import com.back.popspot.domain.payment.service.PaymentService;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.entity.ReservationSlot;
import com.back.popspot.domain.popupStore.repository.ReservationSlotRepository;
import com.back.popspot.domain.reservation.dto.request.ReservationCreateRequest;
import com.back.popspot.domain.reservation.dto.request.ReservationPaymentRequest;
import com.back.popspot.domain.reservation.dto.response.MyReservationResponse;
import com.back.popspot.domain.reservation.dto.response.ReservationCreateResponse;
import com.back.popspot.domain.reservation.dto.response.ReservationPaymentResponse;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.reservation.repository.ReservationRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

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

	@Test
	@DisplayName("내 예약 내역 조회 성공")
	void getMyReservations_success() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore freePopupStore = createPopupStore();
		PopupStore paidPopupStore = createPopupStore();
		ReservationSlot freeSlot = createReservationSlot(freePopupStore);
		ReservationSlot paidSlot = createReservationSlot(paidPopupStore);
		User user = createUser(2L);
		Reservation confirmedReservation = createReservation(100L, user, freeSlot, ReservationStatus.CONFIRMED);
		Reservation canceledReservation = createReservation(101L, user, paidSlot, ReservationStatus.CANCELED);
		Pageable pageable = PageRequest.of(0, 10);
		Page<Reservation> reservations = new PageImpl<>(
			List.of(confirmedReservation, canceledReservation),
			PageRequest.of(0, 10),
			2
		);

		ReflectionTestUtils.setField(freePopupStore, "title", "무료 팝업");
		ReflectionTestUtils.setField(freePopupStore, "location", "서울");
		ReflectionTestUtils.setField(freePopupStore, "feeType", PopupFeeType.FREE);
		ReflectionTestUtils.setField(freePopupStore, "price", null);

		ReflectionTestUtils.setField(paidPopupStore, "title", "유료 팝업");
		ReflectionTestUtils.setField(paidPopupStore, "location", "부산");
		ReflectionTestUtils.setField(paidPopupStore, "feeType", PopupFeeType.PAID);
		ReflectionTestUtils.setField(paidPopupStore, "price", 15000);

		ReflectionTestUtils.setField(freeSlot, "slotDate", LocalDate.of(2026, 6, 20));
		ReflectionTestUtils.setField(freeSlot, "startTime", LocalTime.of(10, 0));
		ReflectionTestUtils.setField(paidSlot, "slotDate", LocalDate.of(2026, 6, 21));
		ReflectionTestUtils.setField(paidSlot, "startTime", LocalTime.of(14, 0));

		when(reservationRepository.findByUserIdAndStatusIn(
			eq(2L),
			eq(List.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELED)),
			any(Pageable.class)
		)).thenReturn(reservations);

		// when
		Page<MyReservationResponse> response = reservationService.getMyReservations(2L, pageable);

		// then
		assertEquals(2, response.getContent().size());

		MyReservationResponse confirmed = response.getContent().get(0);
		assertEquals(100L, confirmed.reservationId());
		assertEquals("무료 팝업", confirmed.popupName());
		assertEquals("서울", confirmed.location());
		assertEquals(LocalDate.of(2026, 6, 20), confirmed.reservationDate());
		assertEquals(LocalTime.of(10, 0), confirmed.reservationTime());
		assertEquals(0, confirmed.price());
		assertEquals(ReservationStatus.CONFIRMED, confirmed.status());

		MyReservationResponse canceled = response.getContent().get(1);
		assertEquals(101L, canceled.reservationId());
		assertEquals("유료 팝업", canceled.popupName());
		assertEquals("부산", canceled.location());
		assertEquals(LocalDate.of(2026, 6, 21), canceled.reservationDate());
		assertEquals(LocalTime.of(14, 0), canceled.reservationTime());
		assertEquals(15000, canceled.price());
		assertEquals(ReservationStatus.CANCELED, canceled.status());

		verify(reservationRepository).findByUserIdAndStatusIn(
			eq(2L),
			eq(List.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELED)),
			argThat(requestedPageable ->
				requestedPageable.getPageNumber() == 0
					&& requestedPageable.getPageSize() == 10
					&& requestedPageable.getSort().equals(Sort.by(
						Sort.Order.desc("reservedAt"),
						Sort.Order.desc("id")
					))
			)
		);
	}

	@Test
	@DisplayName("내 예약 내역이 없으면 빈 페이지를 반환한다")
	void getMyReservations_emptyPage() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		Pageable pageable = PageRequest.of(0, 10);
		Page<Reservation> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

		when(reservationRepository.findByUserIdAndStatusIn(
			eq(2L),
			eq(List.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELED)),
			any(Pageable.class)
		)).thenReturn(emptyPage);

		// when
		Page<MyReservationResponse> response = reservationService.getMyReservations(2L, pageable);

		// then
		assertTrue(response.getContent().isEmpty());
		assertEquals(0, response.getTotalElements());
		verify(reservationRepository).findByUserIdAndStatusIn(
			eq(2L),
			eq(List.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELED)),
			any(Pageable.class)
		);
	}

	@Test
	@DisplayName("예약 선점 성공")
	void createReservation_holdSuccess() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		// 단일 카운터: remaining -1 → 9 (0 이상) 통과
		when(reservationRedisService.decrement(RedisKeys.reservationSlotRemaining(1L))).thenReturn(9L);

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.of(slot));
		when(waitingQueueRedisService.hasProceedPermission(1L, "2")).thenReturn(true);
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull(2L, 1L)).thenReturn(false);
		// DB 저장은 별도 빈에 위임된다. createHeld 결과에 id 만 채워 반환하도록 흉내낸다.
		when(reservationCommandService.save(
			any(User.class),
			any(ReservationSlot.class),
			any(LocalDateTime.class),
			any(LocalDateTime.class)
		)).thenAnswer(invocation -> {
			Reservation reservation = Reservation.createHeld(
				invocation.getArgument(0),
				invocation.getArgument(1),
				invocation.getArgument(2),
				invocation.getArgument(3)
			);
			ReflectionTestUtils.setField(reservation, "id", 100L);
			return reservation;
		});

		// when
		ReservationCreateResponse response = reservationService.createReservation(request, 2L);

		// then
		assertEquals(100L, response.reservationId());
		assertEquals(ReservationStatus.HELD, response.status());
		assertEquals(1L, response.slotId());
		assertEquals(slot.getSlotDate(), response.slotDate());
		assertEquals(slot.getStartTime(), response.startTime());
		assertNotNull(response.heldUntil());
		verify(reservationCommandService).save(
			any(User.class),
			any(ReservationSlot.class),
			any(LocalDateTime.class),
			any(LocalDateTime.class)
		);
		// 정상 흐름: remaining -1 만 일어나고 롤백(remaining +1)은 없어야 한다
		verify(reservationRedisService).decrement(RedisKeys.reservationSlotRemaining(1L));
		verify(reservationRedisService, never()).increment(RedisKeys.reservationSlotRemaining(1L));
	}

	@Test
	@DisplayName("같은 유저 같은 슬롯 중복 선점 실패")
	void createReservation_fail_duplicateHold() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.of(slot));
		when(waitingQueueRedisService.hasProceedPermission(1L, "2")).thenReturn(true);
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull(2L, 1L)).thenReturn(true);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_ALREADY_EXISTS, exception.getErrorCode());
	}

	@Test
	@DisplayName("존재하지 않는 슬롯이면 선점 실패")
	void createReservation_fail_slotNotFound() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.empty());

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_SLOT_NOT_FOUND, exception.getErrorCode());
	}

	@Test
	@DisplayName("예약 가능 기간이 아니면 선점 실패")
	void createReservation_fail_popupReservationNotAvailable() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);

		ReflectionTestUtils.setField(popupStore, "reservationStartAt", LocalDateTime.now().plusDays(1));
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", LocalDateTime.now().plusDays(2));

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.of(slot));
		when(waitingQueueRedisService.hasProceedPermission(1L, "2")).thenReturn(true);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		assertEquals(ErrorCode.POPUP_RESERVATION_NOT_AVAILABLE, exception.getErrorCode());
	}

	@Test
	@DisplayName("DB 저장 실패 시 남은 재고(remaining)를 롤백한다")
	void createReservation_fail_dbSaveRollback() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		// remaining -1 → 9 통과했지만 DB 저장이 실패 → remaining 롤백되어야 함
		when(reservationRedisService.decrement(RedisKeys.reservationSlotRemaining(1L))).thenReturn(9L);

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.of(slot));
		when(waitingQueueRedisService.hasProceedPermission(1L, "2")).thenReturn(true);
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull(2L, 1L)).thenReturn(false);
		when(reservationCommandService.save(
			any(User.class),
			any(ReservationSlot.class),
			any(LocalDateTime.class),
			any(LocalDateTime.class)
		)).thenThrow(new RuntimeException("DB error"));

		// when
		assertThrows(
			RuntimeException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		// 롤백: remaining +1
		verify(reservationRedisService).increment(RedisKeys.reservationSlotRemaining(1L));
	}

	@Test
	@DisplayName("남은 재고(remaining)가 음수면 롤백하고 DB 까지 가지 않는다")
	void createReservation_fail_remainingExhausted() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.of(slot));
		when(waitingQueueRedisService.hasProceedPermission(1L, "2")).thenReturn(true);
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull(2L, 1L)).thenReturn(false);
		// remaining -1 결과가 -1 → 재고 없음
		when(reservationRedisService.decrement(RedisKeys.reservationSlotRemaining(1L))).thenReturn(-1L);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_CAPACITY_EXCEEDED, exception.getErrorCode());
		// 롤백: remaining +1
		verify(reservationRedisService).increment(RedisKeys.reservationSlotRemaining(1L));
		verify(reservationCommandService, never()).save(
			any(User.class),
			any(ReservationSlot.class),
			any(LocalDateTime.class),
			any(LocalDateTime.class)
		);
		verify(reservationWaitlistService).registerIfAvailable(user, slot);
	}

	@Test
	@DisplayName("무료 예약 취소 성공 시 DB 커밋(cancelInTx) 후 Redis remaining을 복구한다")
	void cancelReservation_free_success() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createConfirmedReservation(100L, user, slot);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(paymentRepository.findByReservationIdAndPaymentTypeAndStatus(100L, PaymentType.POPUP, PaymentStatus.PAID))
			.thenReturn(Optional.empty());

		// when
		reservationService.cancelReservation(100L, 2L);

		// then: DB 취소 커밋(cancelInTx) 후 Redis remaining은 즉시 증가하지 않는다
		verify(reservationCommandService).cancelInTx(eq(100L), eq(1L), any(LocalDateTime.class));
		verify(reservationRedisService, never()).increment(any());
		verify(paymentService, never()).cancel(any(Long.class), any(Long.class), any());
	}

	@Test
	@DisplayName("유료 예약 취소 성공 시 결제 환불 후 예약을 취소한다")
	void cancelReservation_paid_success() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createConfirmedReservation(100L, user, slot);
		Payment payment = createPaidReservationPayment(10L, user, reservation);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(paymentRepository.findByReservationIdAndPaymentTypeAndStatus(100L, PaymentType.POPUP, PaymentStatus.PAID))
			.thenReturn(Optional.of(payment));

		// when
		reservationService.cancelReservation(100L, 2L);

		// then
		verify(paymentService).cancel(
			eq(10L),
			eq(2L),
			argThat(request -> "예약 취소".equals(request.cancelReason())
				&& "refund-reservation-100-2".equals(request.idempotencyKey()))
		);
		// then: DB 취소 커밋(cancelInTx) 후 Redis remaining은 즉시 증가하지 않는다
		verify(reservationCommandService).cancelInTx(eq(100L), eq(1L), any(LocalDateTime.class));
		verify(reservationRedisService, never()).increment(any());
	}

	@Test
	@DisplayName("유료 예약 환불 실패 시 예약 취소를 진행하지 않는다")
	void cancelReservation_paid_fail_refund() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createConfirmedReservation(100L, user, slot);
		Payment payment = createPaidReservationPayment(10L, user, reservation);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(paymentRepository.findByReservationIdAndPaymentTypeAndStatus(100L, PaymentType.POPUP, PaymentStatus.PAID))
			.thenReturn(Optional.of(payment));
		when(paymentService.cancel(eq(10L), eq(2L), any()))
			.thenThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED));

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.cancelReservation(100L, 2L)
		);

		// then
		assertEquals(ErrorCode.PAYMENT_CANCEL_FAILED, exception.getErrorCode());
		verify(reservationRepository, never()).cancelConfirmedReservation(
			any(Long.class),
			any(ReservationStatus.class),
			any(ReservationStatus.class),
			any(LocalDateTime.class)
		);
		verify(reservationSlotRepository, never()).decreaseReservedCount(any(Long.class));
		verify(reservationRedisService, never()).increment(any());
	}

	@Test
	@DisplayName("유료 예약 결제 시작 성공")
	void startReservationPayment_paidSuccess() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createHeldReservation(100L, user, slot, LocalDateTime.now().plusMinutes(5));
		ReservationPaymentRequest request = new ReservationPaymentRequest("홍길동", "010-1234-5678", "idem-paid-1");

		ReflectionTestUtils.setField(popupStore, "title", "성수 빈티지 토이 팝업");
		ReflectionTestUtils.setField(popupStore, "feeType", PopupFeeType.PAID);
		ReflectionTestUtils.setField(popupStore, "price", 5000);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(paymentRepository.existsByReservationIdAndPaymentTypeAndStatus(100L, PaymentType.POPUP, PaymentStatus.PAID))
			.thenReturn(false);
		when(paymentReadyService.getOrCreateReservationReadyPayment(
			eq(user),
			eq(reservation),
			eq("성수 빈티지 토이 팝업 예약"),
			eq(5000L),
			eq("idem-paid-1")
		)).thenReturn(Payment.createReadyReservationPayment(
			user,
			reservation,
			"order-123",
			"성수 빈티지 토이 팝업 예약",
			5000L,
			"idem-paid-1"
		));

		// when
		ReservationPaymentResponse response = reservationService.startReservationPayment(100L, 2L, request);

		// then
		assertEquals("성수 빈티지 토이 팝업 예약", response.orderName());
		assertEquals(5000L, response.amount());
		assertNotNull(response.orderId());
		assertEquals("홍길동", ReflectionTestUtils.getField(reservation, "reservationName"));
		assertEquals("010-1234-5678", ReflectionTestUtils.getField(reservation, "reservationPhone"));

		verify(paymentReadyService).getOrCreateReservationReadyPayment(
			eq(user),
			eq(reservation),
			eq("성수 빈티지 토이 팝업 예약"),
			eq(5000L),
			eq("idem-paid-1")
		);
	}

	@Test
	@DisplayName("무료 예약 결제 시작 성공")
	void startReservationPayment_freeSuccess() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createHeldReservation(100L, user, slot, LocalDateTime.now().plusMinutes(5));
		ReservationPaymentRequest request = new ReservationPaymentRequest("홍길동", "010-1234-5678", "idem-free-1");

		ReflectionTestUtils.setField(popupStore, "title", "성수 빈티지 토이 팝업");
		ReflectionTestUtils.setField(popupStore, "location", "서울 성동구 성수동");
		ReflectionTestUtils.setField(popupStore, "feeType", PopupFeeType.FREE);
		LocalDate slotDate = LocalDate.now().plusDays(1);
		ReflectionTestUtils.setField(slot, "slotDate", slotDate);
		ReflectionTestUtils.setField(slot, "startTime", LocalTime.of(15, 0));

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

		// when
		ReservationPaymentResponse response = reservationService.startReservationPayment(100L, 2L, request);

		// then
		assertEquals(100L, response.reservationId());
		assertEquals(ReservationStatus.CONFIRMED, response.status());
		assertEquals("성수 빈티지 토이 팝업", response.popupName());
		assertEquals("서울 성동구 성수동", response.location());
		assertEquals(slotDate, response.reservationDate());
		assertEquals(LocalTime.of(15, 0), response.reservationTime());
		assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
		assertEquals("홍길동", ReflectionTestUtils.getField(reservation, "reservationName"));
		assertEquals("010-1234-5678", ReflectionTestUtils.getField(reservation, "reservationPhone"));
		verify(reservationWaitlistService).deleteByConfirmedReservation(user, slot);
		verify(paymentRepository, never()).save(any(Payment.class));
	}

	@Test
	@DisplayName("같은 멱등성 키 재요청이면 기존 결제를 재응답한다")
	void startReservationPayment_success_reuseExistingPaymentByIdempotencyKey() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createHeldReservation(100L, user, slot, LocalDateTime.now().plusMinutes(5));
		ReservationPaymentRequest request = new ReservationPaymentRequest("홍길동", "010-1234-5678", "idem-paid-2");
		Payment existingPayment = Payment.createReadyReservationPayment(
			user,
			reservation,
			"order-123",
			"성수 빈티지 토이 팝업 예약",
			5000L,
			"idem-paid-2"
		);

		ReflectionTestUtils.setField(popupStore, "title", "성수 빈티지 토이 팝업");
		ReflectionTestUtils.setField(popupStore, "feeType", PopupFeeType.PAID);
		ReflectionTestUtils.setField(popupStore, "price", 5000);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(paymentRepository.existsByReservationIdAndPaymentTypeAndStatus(100L, PaymentType.POPUP, PaymentStatus.PAID))
			.thenReturn(false);
		when(paymentReadyService.getOrCreateReservationReadyPayment(
			eq(user),
			eq(reservation),
			eq("성수 빈티지 토이 팝업 예약"),
			eq(5000L),
			eq("idem-paid-2")
		)).thenReturn(existingPayment);

		// when
		ReservationPaymentResponse response = reservationService.startReservationPayment(100L, 2L, request);

		// then
		assertEquals("order-123", response.orderId());
		assertEquals("성수 빈티지 토이 팝업 예약", response.orderName());
		assertEquals(5000L, response.amount());
		verify(paymentRepository, never()).save(any(Payment.class));
	}

	@Test
	@DisplayName("선점 시간이 만료되면 예약을 만료 처리하고 결제 시작에 실패한다")
	void startReservationPayment_fail_expiredHeldUntil() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createHeldReservation(100L, user, slot, LocalDateTime.now().minusMinutes(1));
		ReservationPaymentRequest request = new ReservationPaymentRequest("홍길동", "010-1234-5678", "idem-expired-1");

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		// expireOne 은 void → 목은 기본적으로 아무 일도 하지 않는다(만료 처리 위임).

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.startReservationPayment(100L, 2L, request)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_PAYMENT_EXPIRED, exception.getErrorCode());
		verify(reservationExpirationService).expireOne(eq(reservation), any(LocalDateTime.class));
		verify(paymentRepository, never()).save(any(Payment.class));
	}

	@Test
	@DisplayName("본인 예약이 아니면 취소 실패")
	void cancelReservation_fail_forbidden() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createConfirmedReservation(100L, user, slot);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.cancelReservation(100L, 3L)
		);

		// then
		assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
	}

	@Test
	@DisplayName("확정 상태가 아니면 취소 실패")
	void cancelReservation_fail_notConfirmed() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createReservation(100L, user, slot, ReservationStatus.HELD);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.cancelReservation(100L, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED_STATUS, exception.getErrorCode());
	}

	@Test
	@DisplayName("취소 기한이 지나면 취소 실패")
	void cancelReservation_fail_deadlinePassed() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createConfirmedReservation(100L, user, slot);
		ReflectionTestUtils.setField(slot, "slotDate", LocalDate.now());

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.cancelReservation(100L, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_CANCEL_DEADLINE_PASSED, exception.getErrorCode());
	}

	@Test
	@DisplayName("조건부 상태 변경에 실패하면 취소 실패")
	void cancelReservation_fail_conditionalCancelUpdate() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createConfirmedReservation(100L, user, slot);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(paymentRepository.findByReservationIdAndPaymentTypeAndStatus(100L, PaymentType.POPUP, PaymentStatus.PAID))
			.thenReturn(Optional.empty());
		// DB 취소 트랜잭션이 조건부 업데이트 실패로 롤백(예외) 된 상황을 흉내낸다.
		doThrow(new BusinessException(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED_STATUS))
			.when(reservationCommandService).cancelInTx(eq(100L), eq(1L), any(LocalDateTime.class));

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.cancelReservation(100L, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED_STATUS, exception.getErrorCode());
		// 커밋 실패 → Redis 복구(INCR) 없음
		verify(reservationRedisService, never()).increment(any());
	}

	// ────────── admission 검증 관련 신규 케이스 ──────────

	@Test
	@DisplayName("입장 허가 후 예약 성공 시 proceed flag를 정확히 1번 소각한다")
	void createReservation_success_revokesAdmissionFlagOnHold() {
		// given
		ReservationService reservationService = new ReservationService(
		reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.of(slot));
		when(waitingQueueRedisService.hasProceedPermission(1L, "2")).thenReturn(true);
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull(2L, 1L)).thenReturn(false);
		when(reservationRedisService.decrement(RedisKeys.reservationSlotRemaining(1L))).thenReturn(9L);
		when(reservationCommandService.save(
			any(User.class),
			any(ReservationSlot.class),
			any(LocalDateTime.class),
			any(LocalDateTime.class)
		)).thenAnswer(invocation -> {
			Reservation reservation = Reservation.createHeld(
				invocation.getArgument(0),
				invocation.getArgument(1),
				invocation.getArgument(2),
				invocation.getArgument(3)
			);
			ReflectionTestUtils.setField(reservation, "id", 100L);
			return reservation;
		});

		// when
		reservationService.createReservation(request, 2L);

		// then: flag는 정확히 1회 소각되어야 한다
		verify(waitingQueueRedisService).revokeProceedPermission(1L, "2");
	}

	@Test
	@DisplayName("proceed flag 없으면 RESERVATION_ADMISSION_REQUIRED, remaining 차감 없음")
	void createReservation_fail_admissionRequired() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.of(slot));
		when(waitingQueueRedisService.hasProceedPermission(1L, "2")).thenReturn(false);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_ADMISSION_REQUIRED, exception.getErrorCode());
		// remaining 카운터에 전혀 손대지 않았는지 확인
		verify(reservationRedisService, never()).increment(any());
	}

	@Test
	@DisplayName("admission 통과 후 capacity 초과 시 proceed flag를 소각하지 않는다 (재시도 가능)")
	void createReservation_fail_capacityExceeded_doesNotRevokeFlag() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.of(slot));
		when(waitingQueueRedisService.hasProceedPermission(1L, "2")).thenReturn(true);
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull(2L, 1L)).thenReturn(false);
		when(reservationRedisService.decrement(RedisKeys.reservationSlotRemaining(1L))).thenReturn(-1L);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_CAPACITY_EXCEEDED, exception.getErrorCode());
		// decrement 실패 분기에서는 flag를 소각하지 않아 동일 TTL 내 재시도가 가능해야 한다
		verify(waitingQueueRedisService, never()).revokeProceedPermission(1L, "2");
	}

	@Test
	@DisplayName("remaining 차감 성공 후 DB save 실패 시 proceed flag는 이미 소각된 상태다 (characterization)")
	void createReservation_fail_dbSave_proceedFlagAlreadyRevoked() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			paymentService,
			userRepository,
			reservationExpirationService,
			reservationCommandService,
			reservationWaitlistService,
    		redisTemplate,
			waitingQueueRedisService,
			paymentReadyService,
			reservationRedisService
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findByIdWithPopupStore(1L)).thenReturn(Optional.of(slot));
		when(waitingQueueRedisService.hasProceedPermission(1L, "2")).thenReturn(true);
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull(2L, 1L)).thenReturn(false);
		when(reservationRedisService.decrement(RedisKeys.reservationSlotRemaining(1L))).thenReturn(9L);
		when(reservationCommandService.save(
			any(User.class),
			any(ReservationSlot.class),
			any(LocalDateTime.class),
			any(LocalDateTime.class)
		)).thenThrow(new RuntimeException("DB error"));

		// when
		assertThrows(
			RuntimeException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then: flag는 decrement 직후에 이미 소각됐다 — 재진입하려면 대기열 재진입이 필요하다
		verify(waitingQueueRedisService).revokeProceedPermission(1L, "2");
		// remaining은 DB 실패 catch 블록에서 롤백된다
		verify(reservationRedisService).increment(RedisKeys.reservationSlotRemaining(1L));
	}

	// TODO(이번 PR 범위 외 누락 테스트):
	//  1. createReservation_fail_slotAlreadyStarted — RESERVATION_SLOT_ALREADY_STARTED
	//     slotDate/startTime 이 현재 시각보다 과거인 케이스가 단위·통합 어디에도 없다.
	//  2. createReservation_fail_userNotFound — RESOURCE_NOT_FOUND
	//     userRepository.findById 가 empty 를 반환하는 케이스가 단위·통합 어디에도 없다.

	private PopupStore createPopupStore() {
		PopupStore popupStore = new PopupStore();
		LocalDateTime now = LocalDateTime.now();

		ReflectionTestUtils.setField(popupStore, "id", 1L);
		ReflectionTestUtils.setField(popupStore, "reservationStartAt", now.minusDays(1));
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", now.plusDays(1));
		ReflectionTestUtils.setField(popupStore, "feeType", PopupFeeType.FREE);

		return popupStore;
	}

	private ReservationSlot createReservationSlot(PopupStore popupStore) {
		ReservationSlot slot = new ReservationSlot();

		ReflectionTestUtils.setField(slot, "id", 1L);
		ReflectionTestUtils.setField(slot, "popupStore", popupStore);
		ReflectionTestUtils.setField(slot, "slotDate", LocalDate.now().plusDays(1));
		ReflectionTestUtils.setField(slot, "startTime", LocalTime.of(10, 0));
		ReflectionTestUtils.setField(slot, "capacity", 10);
		ReflectionTestUtils.setField(slot, "reservedCount", 0);

		return slot;
	}

	private User createUser(Long userId) {
		User user = User.create("user@test.com", "user");
		ReflectionTestUtils.setField(user, "id", userId);

		return user;
	}

	private Reservation createConfirmedReservation(Long reservationId, User user, ReservationSlot slot) {
		return createReservation(reservationId, user, slot, ReservationStatus.CONFIRMED);
	}

	private Reservation createHeldReservation(Long reservationId, User user, ReservationSlot slot, LocalDateTime heldUntil) {
		Reservation reservation = createReservation(reservationId, user, slot, ReservationStatus.HELD);
		ReflectionTestUtils.setField(reservation, "heldUntil", heldUntil);
		return reservation;
	}

	private Payment createPaidReservationPayment(Long paymentId, User user, Reservation reservation) {
		Payment payment = Payment.createReadyReservationPayment(
			user,
			reservation,
			"order-id",
			"예약 결제",
			5000L,
			"payment-idempotency-key"
		);
		ReflectionTestUtils.setField(payment, "id", paymentId);
		ReflectionTestUtils.setField(payment, "status", PaymentStatus.PAID);
		ReflectionTestUtils.setField(payment, "paymentKey", "payment-key");
		return payment;
	}

	private Reservation createReservation(Long reservationId, User user, ReservationSlot slot, ReservationStatus status) {
		Reservation reservation = new Reservation();

		ReflectionTestUtils.setField(reservation, "id", reservationId);
		ReflectionTestUtils.setField(reservation, "user", user);
		ReflectionTestUtils.setField(reservation, "slot", slot);
		ReflectionTestUtils.setField(reservation, "status", status);

		return reservation;
	}
}
