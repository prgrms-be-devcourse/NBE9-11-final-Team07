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

import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;
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
import com.back.popspot.global.exception.ReservationPaymentExpiredException;
import com.back.popspot.global.redis.RedisKeys;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

	private static final long HOLD_MINUTES = 5L;
	private static final Sort DEFAULT_RESERVATION_SORT = Sort.by(
		Sort.Order.desc("reservedAt"),
		Sort.Order.desc("id")
	);

	private final ReservationRepository reservationRepository;
	private final ReservationSlotRepository reservationSlotRepository;
	private final PaymentRepository paymentRepository;
	private final UserRepository userRepository;
	private final ReservationExpirationService reservationExpirationService;
	private final RedisTemplate<String, Long> redisTemplate;

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

	@Transactional
	public ReservationCreateResponse createReservation(ReservationCreateRequest request, Long userId) {
		ReservationSlot slot = reservationSlotRepository.findById(request.slotId())
			.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_SLOT_NOT_FOUND));

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
		if (reservationRepository.existsByUserIdAndSlotId(user.getId(), slot.getId())) {
			throw new BusinessException(ErrorCode.RESERVATION_ALREADY_EXISTS);
		}

		Long slotId = slot.getId();
		String remainingKey = RedisKeys.reservationSlotRemaining(slotId);

		// 단일 카운터 - 남은 재고(remaining) 선차감. DECR 반환값만으로 동시성 제어가 완결된다.
		Long after = redisTemplate.opsForValue().decrement(remainingKey);
		if (after == null || after < 0) {
			// 남은 자리 없음/미초기화 → remaining 롤백
			redisTemplate.opsForValue().increment(remainingKey);
			throw new BusinessException(ErrorCode.RESERVATION_CAPACITY_EXCEEDED);
		}

		// 실제 DB 반영. 워커/DB 처리 실패 시 remaining 롤백
		try {
			LocalDateTime heldUntil = now.plusMinutes(HOLD_MINUTES);
			Reservation reservation = Reservation.createHeld(user, slot, now, heldUntil);
			reservationRepository.save(reservation);

			return ReservationCreateResponse.from(reservation);
		} catch (RuntimeException e) {
			redisTemplate.opsForValue().increment(remainingKey);
			throw e;
		}
	}

	@Transactional
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

		if (paymentRepository.existsByReservationIdAndPaymentTypeAndStatus(
			reservationId,
			PaymentType.POPUP,
			PaymentStatus.PAID
		)) {
			// TODO: 결제 도메인 환불 요청 연동
		}

		int canceledCount = reservationRepository.cancelConfirmedReservation(
			reservationId,
			ReservationStatus.CONFIRMED,
			ReservationStatus.CANCELED,
			now
		);
		if (canceledCount == 0) {
			throw new BusinessException(ErrorCode.RESERVATION_CANCEL_NOT_ALLOWED_STATUS);
		}

		int updatedCount = reservationSlotRepository.decreaseReservedCount(reservation.getSlot().getId());
		if (updatedCount == 0) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
		}

		// 취소로 자리가 비었으므로 재고 카운터 복구
		redisTemplate.opsForValue().increment(RedisKeys.reservationSlotRemaining(reservation.getSlot().getId()));
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
			reservationExpirationService.expireIfHeldAndExpired(reservation, now);
			throw new ReservationPaymentExpiredException(ErrorCode.RESERVATION_PAYMENT_EXPIRED);
		}

		reservation.updateReservationInfo(request.name(), request.phone());

		PopupStore popupStore = reservation.getSlot().getPopupStore();
		if (popupStore.getFeeType() == PopupFeeType.FREE) {
			reservation.confirm();
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
}
