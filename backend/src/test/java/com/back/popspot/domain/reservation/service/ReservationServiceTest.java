package com.back.popspot.domain.reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.repository.PaymentRepository;
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
	private UserRepository userRepository;

	@Mock
	private ReservationExpirationService reservationExpirationService;

	@Mock
	private RedisTemplate<String, Long> redisTemplate;

	@Mock
	private ValueOperations<String, Long> valueOperations;

	@Test
	@DisplayName("내 예약 내역 조회 성공")
	void getMyReservations_success() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			userRepository,
			reservationExpirationService,
			redisTemplate
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
			userRepository,
			reservationExpirationService,
			redisTemplate
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
			userRepository,
			reservationExpirationService,
			redisTemplate
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		// 이중 카운터: req +1 → 1 (capacity 10 이하), remaining -1 → 9 (0 이상) 통과
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(RedisKeys.reservationSlotReqCount(1L))).thenReturn(1L);
		when(valueOperations.decrement(RedisKeys.reservationSlotRemaining(1L))).thenReturn(9L);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotId(2L, 1L)).thenReturn(false);
		when(reservationSlotRepository.increaseReservedCountIfAvailable(1L)).thenReturn(1);
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
			Reservation reservation = invocation.getArgument(0);
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
		verify(reservationRepository).save(any(Reservation.class));
		// 정상 흐름: req +1, remaining -1 만 일어나고 롤백(req -1, remaining +1)은 없어야 한다
		verify(valueOperations).increment(RedisKeys.reservationSlotReqCount(1L));
		verify(valueOperations).decrement(RedisKeys.reservationSlotRemaining(1L));
		verify(valueOperations, never()).decrement(RedisKeys.reservationSlotReqCount(1L));
		verify(valueOperations, never()).increment(RedisKeys.reservationSlotRemaining(1L));
	}

	@Test
	@DisplayName("같은 유저 같은 슬롯 중복 선점 실패")
	void createReservation_fail_duplicateHold() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			userRepository,
			reservationExpirationService,
			redisTemplate
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotId(2L, 1L)).thenReturn(true);

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
			userRepository,
			reservationExpirationService,
			redisTemplate
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.empty());

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
			userRepository,
			reservationExpirationService,
			redisTemplate
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);

		ReflectionTestUtils.setField(popupStore, "reservationStartAt", LocalDateTime.now().plusDays(1));
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", LocalDateTime.now().plusDays(2));

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		assertEquals(ErrorCode.POPUP_RESERVATION_NOT_AVAILABLE, exception.getErrorCode());
	}

	@Test
	@DisplayName("DB 단계 정원 초과로 선점 실패 시 두 카운터를 모두 롤백한다")
	void createReservation_fail_capacityExceeded() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			userRepository,
			reservationExpirationService,
			redisTemplate
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		// 카운터는 통과(req=1, remaining=9)했지만 DB 조건부 업데이트가 0건 → 롤백되어야 함
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(RedisKeys.reservationSlotReqCount(1L))).thenReturn(1L);
		when(valueOperations.decrement(RedisKeys.reservationSlotRemaining(1L))).thenReturn(9L);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotId(2L, 1L)).thenReturn(false);
		when(reservationSlotRepository.increaseReservedCountIfAvailable(1L)).thenReturn(0);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_CAPACITY_EXCEEDED, exception.getErrorCode());
		// 롤백: remaining +1, req -1
		verify(valueOperations).increment(RedisKeys.reservationSlotRemaining(1L));
		verify(valueOperations).decrement(RedisKeys.reservationSlotReqCount(1L));
		verify(reservationRepository, never()).save(any(Reservation.class));
	}

	@Test
	@DisplayName("요청 카운터(req)가 capacity 를 넘으면 DB 까지 가기 전에 즉시 차단하고 req 를 되돌린다")
	void createReservation_fail_reqCounterExceeded() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			userRepository,
			reservationExpirationService,
			redisTemplate
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotId(2L, 1L)).thenReturn(false);
		// req +1 결과가 11 → capacity(10) 초과
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(RedisKeys.reservationSlotReqCount(1L))).thenReturn(11L);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_CAPACITY_EXCEEDED, exception.getErrorCode());
		// 넘친 req 는 즉시 -1 로 복구, remaining 과 DB 는 손도 대지 않는다
		verify(valueOperations).decrement(RedisKeys.reservationSlotReqCount(1L));
		verify(valueOperations, never()).decrement(RedisKeys.reservationSlotRemaining(1L));
		verify(reservationSlotRepository, never()).increaseReservedCountIfAvailable(1L);
		verify(reservationRepository, never()).save(any(Reservation.class));
	}

	@Test
	@DisplayName("남은 재고(remaining)가 음수면 두 카운터를 모두 롤백하고 DB 까지 가지 않는다")
	void createReservation_fail_remainingExhausted() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			userRepository,
			reservationExpirationService,
			redisTemplate
		);
		ReservationCreateRequest request = new ReservationCreateRequest(1L);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);

		when(reservationSlotRepository.findById(1L)).thenReturn(Optional.of(slot));
		when(userRepository.findById(2L)).thenReturn(Optional.of(user));
		when(reservationRepository.existsByUserIdAndSlotId(2L, 1L)).thenReturn(false);
		// req 는 통과(1)했지만 remaining -1 결과가 -1 → 재고 없음
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(RedisKeys.reservationSlotReqCount(1L))).thenReturn(1L);
		when(valueOperations.decrement(RedisKeys.reservationSlotRemaining(1L))).thenReturn(-1L);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.createReservation(request, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_CAPACITY_EXCEEDED, exception.getErrorCode());
		// 롤백: remaining +1, req -1
		verify(valueOperations).increment(RedisKeys.reservationSlotRemaining(1L));
		verify(valueOperations).decrement(RedisKeys.reservationSlotReqCount(1L));
		verify(reservationSlotRepository, never()).increaseReservedCountIfAvailable(1L);
		verify(reservationRepository, never()).save(any(Reservation.class));
	}

	@Test
	@DisplayName("예약 취소 성공")
	void cancelReservation_success() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			userRepository,
			reservationExpirationService,
			redisTemplate
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createConfirmedReservation(100L, user, slot);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(paymentRepository.existsByReservationIdAndPaymentTypeAndStatus(100L, PaymentType.POPUP, PaymentStatus.PAID))
			.thenReturn(false);
		when(reservationRepository.cancelConfirmedReservation(
			eq(100L),
			eq(ReservationStatus.CONFIRMED),
			eq(ReservationStatus.CANCELED),
			any(LocalDateTime.class)
		)).thenReturn(1);
		when(reservationSlotRepository.decreaseReservedCount(1L)).thenReturn(1);

		// when
		reservationService.cancelReservation(100L, 2L);

		// then
		verify(reservationRepository).cancelConfirmedReservation(
			eq(100L),
			eq(ReservationStatus.CONFIRMED),
			eq(ReservationStatus.CANCELED),
			any(LocalDateTime.class)
		);
		verify(reservationSlotRepository).decreaseReservedCount(1L);
	}

	@Test
	@DisplayName("유료 예약 결제 시작 성공")
	void startReservationPayment_paidSuccess() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			userRepository,
			reservationExpirationService,
			redisTemplate
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
		when(paymentRepository.findByIdempotencyKey("idem-paid-1")).thenReturn(Optional.empty());
		when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// when
		ReservationPaymentResponse response = reservationService.startReservationPayment(100L, 2L, request);

		// then
		assertEquals("성수 빈티지 토이 팝업 예약", response.orderName());
		assertEquals(5000L, response.amount());
		assertNotNull(response.orderId());
		assertEquals("홍길동", ReflectionTestUtils.getField(reservation, "reservationName"));
		assertEquals("010-1234-5678", ReflectionTestUtils.getField(reservation, "reservationPhone"));

		verify(paymentRepository).save(argThat(payment ->
			payment.getReservation().equals(reservation)
				&& payment.getUser().equals(user)
				&& payment.getPaymentType() == PaymentType.POPUP
				&& payment.getStatus() == PaymentStatus.READY
				&& "idem-paid-1".equals(payment.getIdempotencyKey())
				&& "성수 빈티지 토이 팝업 예약".equals(payment.getOrderName())
				&& payment.getAmount() == 5000L
		));
	}

	@Test
	@DisplayName("무료 예약 결제 시작 성공")
	void startReservationPayment_freeSuccess() {
		// given
		ReservationService reservationService = new ReservationService(
			reservationRepository,
			reservationSlotRepository,
			paymentRepository,
			userRepository,
			reservationExpirationService,
			redisTemplate
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
			userRepository,
			reservationExpirationService,
			redisTemplate
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

		ReflectionTestUtils.setField(popupStore, "feeType", PopupFeeType.PAID);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(paymentRepository.existsByReservationIdAndPaymentTypeAndStatus(100L, PaymentType.POPUP, PaymentStatus.PAID))
			.thenReturn(false);
		when(paymentRepository.findByIdempotencyKey("idem-paid-2")).thenReturn(Optional.of(existingPayment));

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
			userRepository,
			reservationExpirationService,
			redisTemplate
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createHeldReservation(100L, user, slot, LocalDateTime.now().minusMinutes(1));
		ReservationPaymentRequest request = new ReservationPaymentRequest("홍길동", "010-1234-5678", "idem-expired-1");

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(reservationExpirationService.expireIfHeldAndExpired(eq(reservation), any(LocalDateTime.class)))
			.thenReturn(true);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.startReservationPayment(100L, 2L, request)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_PAYMENT_EXPIRED, exception.getErrorCode());
		verify(reservationExpirationService).expireIfHeldAndExpired(eq(reservation), any(LocalDateTime.class));
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
			userRepository,
			reservationExpirationService,
			redisTemplate
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
			userRepository,
			reservationExpirationService,
			redisTemplate
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
			userRepository,
			reservationExpirationService,
			redisTemplate
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
			userRepository,
			reservationExpirationService,
			redisTemplate
		);
		PopupStore popupStore = createPopupStore();
		ReservationSlot slot = createReservationSlot(popupStore);
		User user = createUser(2L);
		Reservation reservation = createConfirmedReservation(100L, user, slot);

		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(paymentRepository.existsByReservationIdAndPaymentTypeAndStatus(100L, PaymentType.POPUP, PaymentStatus.PAID))
			.thenReturn(false);
		when(reservationRepository.cancelConfirmedReservation(
			eq(100L),
			eq(ReservationStatus.CONFIRMED),
			eq(ReservationStatus.CANCELED),
			any(LocalDateTime.class)
		)).thenReturn(0);

		// when
		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> reservationService.cancelReservation(100L, 2L)
		);

		// then
		assertEquals(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED_STATUS, exception.getErrorCode());
		verify(reservationSlotRepository, never()).decreaseReservedCount(any(Long.class));
	}

	private PopupStore createPopupStore() {
		PopupStore popupStore = new PopupStore();
		LocalDateTime now = LocalDateTime.now();

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

	private Reservation createReservation(Long reservationId, User user, ReservationSlot slot, ReservationStatus status) {
		Reservation reservation = new Reservation();

		ReflectionTestUtils.setField(reservation, "id", reservationId);
		ReflectionTestUtils.setField(reservation, "user", user);
		ReflectionTestUtils.setField(reservation, "slot", slot);
		ReflectionTestUtils.setField(reservation, "status", status);

		return reservation;
	}
}
