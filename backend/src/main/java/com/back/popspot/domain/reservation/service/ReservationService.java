package com.back.popspot.domain.reservation.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

import jakarta.annotation.PostConstruct;
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
	private final StringRedisTemplate stringRedisTemplate;      // [추가] Redis 템플릿
	private DefaultRedisScript<Long> reserveScript;             // [추가] Lua 스크립트 객체

	// [추가] 서버 시작 시 Lua 스크립트 파일을 로드해 캐싱
	@PostConstruct
	void initScript() {
		this.reserveScript = new DefaultRedisScript<>();
		this.reserveScript.setLocation(new ClassPathResource("lua/reserve_slot.lua"));
		this.reserveScript.setResultType(Long.class);
	}

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
		if (!slotDateTime.isAfter(now)) {
			throw new BusinessException(ErrorCode.RESERVATION_SLOT_ALREADY_STARTED);
		}

		PopupStore popupStore = slot.getPopupStore();
		if (now.isBefore(popupStore.getReservationStartAt()) || now.isAfter(popupStore.getReservationEndAt())) {
			throw new BusinessException(ErrorCode.POPUP_RESERVATION_NOT_AVAILABLE);
		}

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (reservationRepository.existsByUserIdAndSlotId(user.getId(), slot.getId())) {
			throw new BusinessException(ErrorCode.RESERVATION_ALREADY_EXISTS);
		}

		// [추가] Redis 키가 없으면 현재 reservedCount로 초기화 (최초 1회만 세팅)
		String redisKey = RedisKeys.reservationSlotReserved(slot.getId());
		stringRedisTemplate.opsForValue().setIfAbsent(redisKey, String.valueOf(slot.getReservedCount()));

		// [추가] Lua 스크립트로 GET+비교+INCR을 원자적으로 실행 → race condition 제거
		// 기존: reservationSlotRepository.increaseReservedCountIfAvailable (DB 원자적 업데이트)
		// 변경: Redis Lua 스크립트 (capacity 비교 + INCR을 Redis 단일 스레드에서 원자 실행)
		Long result = stringRedisTemplate.execute(
				reserveScript,
				List.of(redisKey),
				String.valueOf(slot.getCapacity())
		);
		if (result == null || result < 0) {
			throw new BusinessException(ErrorCode.RESERVATION_CAPACITY_EXCEEDED);
		}

		LocalDateTime heldUntil = now.plusMinutes(HOLD_MINUTES);
		Reservation reservation = Reservation.createHeld(user, slot, now, heldUntil);
		reservationRepository.save(reservation);

		return ReservationCreateResponse.from(reservation);
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
		LocalDateTime slotDateTime = LocalDateTime.of(
				reservation.getSlot().getSlotDate(),
				reservation.getSlot().getStartTime()
		);
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