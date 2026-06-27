package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.payment.dto.PaymentCancelRequest;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.repository.PaymentRepository;
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
import com.back.popspot.global.exception.ReservationPaymentExpiredException;
import com.back.popspot.global.queue.service.WaitingQueueRedisService;
import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

	private static final long HOLD_MINUTES = 5L;
	private static final Sort DEFAULT_RESERVATION_SORT = Sort.by(
		Sort.Order.desc("reservedAt"),
		Sort.Order.desc("id")
	);

	private final ReservationRepository reservationRepository;
	private final ReservationSlotRepository reservationSlotRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentService paymentService;
	private final UserRepository userRepository;
	private final ReservationExpirationService reservationExpirationService;
	private final ReservationCommandService reservationCommandService;
	private final ReservationWaitlistService reservationWaitlistService;
	private final RedisTemplate<String, Long> redisTemplate;
	private final WaitingQueueRedisService waitingQueueRedisService;

	@Transactional(readOnly = true)
	public Page<MyReservationResponse> getMyReservations(Long userId, Pageable pageable) {

		Pageable effectivePageable = PageRequest.of(
			pageable.getPageNumber(),
			pageable.getPageSize(),
			DEFAULT_RESERVATION_SORT
		);

		return reservationRepository.findByUserIdAndStatusIn(
			userId,
			List.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELED),
			effectivePageable
		).map(MyReservationResponse::from);
	}

	// @Transactional 을 두지 않는다. Redis 차감/롤백을 트랜잭션 밖에서 수행해 커넥션 점유를 막고,
	// DB 저장은 별도 빈(ReservationCommandService)의 트랜잭션에 위임한다.
	public ReservationCreateResponse createReservation(ReservationCreateRequest request, Long userId) {
		// 1. 검증용 DB 읽기는 Redis 차감 전에 끝낸다. (popupStore 까지 join fetch 로 함께 로딩)
		//    각 조회는 짧은 단일 트랜잭션으로 즉시 커넥션을 반납한다.
		ReservationSlot slot = reservationSlotRepository.findByIdWithPopupStore(request.slotId())
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SLOT_NOT_FOUND));

		long popupId = slot.getPopupStore().getId();
		if (!waitingQueueRedisService.hasProceedPermission(popupId, userId.toString())) {
			throw new BusinessException(ErrorCode.RESERVATION_ADMISSION_REQUIRED);
		}

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime slotDateTime = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
		// 이미 지난 슬롯 (정책 회의후 제거 검토)
		if (!slotDateTime.isAfter(now)) {
			throw new BusinessException(ErrorCode.RESERVATION_SLOT_ALREADY_STARTED);
		}

		PopupStore popupStore = slot.getPopupStore();
		// 예약 가능 기간 검증
		if (now.isBefore(popupStore.getReservationStartAt()) || now.isAfter(popupStore.getReservationEndAt())) {
			throw new BusinessException(ErrorCode.POPUP_RESERVATION_NOT_AVAILABLE);
		}

		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		// 중복 예약 방지
		if (reservationRepository.existsByUserIdAndSlotIdAndActiveUniqueKeyIsNotNull(user.getId(), slot.getId())) {
			throw new BusinessException(ErrorCode.RESERVATION_ALREADY_EXISTS);
		}

		Long slotId = slot.getId();
		String remainingKey = RedisKeys.reservationSlotRemaining(slotId);

		// 2. 모든 검증 통과 후 단일 카운터(remaining) 선차감. DECR 반환값만으로 동시성 제어가 완결된다.
		Long after = redisTemplate.opsForValue().decrement(remainingKey);
		if (after == null || after < 0) {
			// 남은 자리 없음/미초기화 → remaining 롤백
			redisTemplate.opsForValue().increment(remainingKey);
			registerWaitlist(user, slot);
			throw new BusinessException(ErrorCode.RESERVATION_CAPACITY_EXCEEDED);
		}

		// 차감 성공 = 예약 확정. proceed flag를 즉시 소각해 동일 팝업 재사용을 막는다.
		waitingQueueRedisService.revokeProceedPermission(popupId, userId.toString());

		// 3. DB 저장은 별도 빈의 @Transactional 메서드에 위임한다.
		//    커밋 단계 실패까지 여기 try-catch 가 잡아 remaining 을 롤백한다.
		try {
			LocalDateTime heldUntil = now.plusMinutes(HOLD_MINUTES);
			Reservation reservation = reservationCommandService.save(user, slot, now, heldUntil);

			return ReservationCreateResponse.from(reservation);
		} catch (RuntimeException e) {
			redisTemplate.opsForValue().increment(remainingKey);
			throw e;
		}
	}

	// @Transactional 제거 <- 검증+환불은 트랜잭션 밖, DB취소는 cancelIntx에 위임, 그 뒤 Redis INCR
	public void cancelReservation(Long reservationId, Long userId) {
		Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (!reservation.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
			throw new BusinessException(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED_STATUS);
		}

		LocalDateTime now = LocalDateTime.now();
		if (!now.isBefore(reservation.getSlot().getSlotDate().atStartOfDay())) {
			throw new BusinessException(ErrorCode.RESERVATION_CANCEL_DEADLINE_PASSED);
		}

		// TODO: 환불-취소 정합성은 결제 도메인 정책 확정 후 재설계 필요. 현재는 호출 위치만 유지.
		paymentRepository.findByReservationIdAndPaymentTypeAndStatus(
			reservationId,
			PaymentType.POPUP,
			PaymentStatus.PAID
		).ifPresent(payment -> paymentService.cancel(
			payment.getId(),
			userId,
			new PaymentCancelRequest("예약 취소", "refund-reservation-" + reservationId + "-" + userId)
		));

		Long slotId = reservation.getSlot().getId();

		// DB 취소는 별도 트랜잭션 빈에 위임 (리턴 = 커밋 완료)
		reservationCommandService.cancelInTx(reservationId, slotId, now);
	}

	@Transactional(noRollbackFor = ReservationPaymentExpiredException.class)
	public ReservationPaymentResponse startReservationPayment(
		Long reservationId,
		Long userId,
		ReservationPaymentRequest request
	) {
		Reservation reservation = reservationRepository.findById(reservationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (!reservation.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		if (reservation.getStatus() != ReservationStatus.HELD) {
			throw new BusinessException(ErrorCode.RESERVATION_PAYMENT_NOT_ALLOWED_STATUS);
		}

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime slotDateTime = LocalDateTime.of(reservation.getSlot().getSlotDate(),
			reservation.getSlot().getStartTime());
		// 이미 지난 슬롯 (정책 회의후 제거 검토)
		if (!slotDateTime.isAfter(now)) {
			throw new BusinessException(ErrorCode.RESERVATION_SLOT_ALREADY_STARTED);
		}

		if (!reservation.getHeldUntil().isAfter(now)) {
			reservationExpirationService.expireOne(reservation, now);
			throw new ReservationPaymentExpiredException(ErrorCode.RESERVATION_PAYMENT_EXPIRED);
		}

		reservation.updateReservationInfo(request.name(), request.phone());

		PopupStore popupStore = reservation.getSlot().getPopupStore();
		if (popupStore.getFeeType() == PopupFeeType.FREE) {
			reservation.confirm();
			reservationWaitlistService.deleteByConfirmedReservation(reservation.getUser(), reservation.getSlot());
			return ReservationPaymentResponse.free(reservation);
		}

		if (paymentRepository.existsByReservationIdAndPaymentTypeAndStatus(
			reservationId,
			PaymentType.POPUP,
			PaymentStatus.PAID
		)) {
			throw new BusinessException(ErrorCode.RESERVATION_PAYMENT_ALREADY_COMPLETED);
		}

		// 재요청이면 기존 결제를 그대로 반환한다.
		Payment existingIdempotentPayment = paymentRepository.findByIdempotencyKey(request.idempotencyKey())
			.orElse(null);
		if (existingIdempotentPayment != null) {
			if (!existingIdempotentPayment.getReservation().getId().equals(reservationId)
				|| !existingIdempotentPayment.getUser().getId().equals(userId)
				|| existingIdempotentPayment.getPaymentType() != PaymentType.POPUP) {
				throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
			}
			return ReservationPaymentResponse.paid(existingIdempotentPayment);
		}

		// 첫 결제 진입이면 READY 상태의 결제를 새로 만든다.
		Payment payment = Payment.createReadyReservationPayment(
			reservation.getUser(),
			reservation,
			UUID.randomUUID().toString(),
			popupStore.getTitle() + " 예약",
			popupStore.getPrice(),
			request.idempotencyKey()
		);
		paymentRepository.save(payment);

		return ReservationPaymentResponse.paid(payment);
	}

	private void registerWaitlist(User user, ReservationSlot slot) {
		try {
			reservationWaitlistService.registerIfAvailable(user, slot);
		} catch (RuntimeException exception) {
			log.warn("Reservation waitlist registration failed. userId={}, slotId={}",
				user.getId(), slot.getId(), exception);
		}
	}
}
