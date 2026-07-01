package com.back.popspot.domain.payment.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentReadyService {

	private static final List<PaymentStatus> ACTIVE_PAYMENT_STATUSES = List.of(
		PaymentStatus.READY,
		PaymentStatus.CONFIRMING,
		PaymentStatus.PAID
	);

	private final PaymentRepository paymentRepository;

	// 예약 결제용 READY 결제를 조회하거나 생성
	// reservation 구조는 최대한 건드리지 않고
	// payment 쪽에서 예약에 대해 결제가 중복 생성되지 않도록 최대한 방어
	@Transactional
	public Payment getOrCreateReservationReadyPayment(
		User user,
		Reservation reservation,
		String orderName,
		long amount,
		String idempotencyKey
	) {
		Payment idempotentPayment = findIdempotentReservationPayment(
			user,
			reservation,
			idempotencyKey
		);

		if (idempotentPayment != null) {
			return idempotentPayment;
		}

		Payment activePayment = findActiveReservationPayment(reservation.getId()).orElse(null);

		if (activePayment != null) {
			return activePayment;
		}

		return createReservationReadyPayment(
			user,
			reservation,
			orderName,
			amount,
			idempotencyKey
		);
	}

	// 굿즈 주문 결제용 READY 결제를 조회하거나 생성한다.
	// goods 구조는 최대한 건드리지 않고
	// payment 쪽에서 굿즈 주문에 대해 결제가 중복되어 생성되지 않도록 방어
	@Transactional
	public Payment getOrCreateGoodsOrderReadyPayment(
		User user,
		GoodsOrder goodsOrder,
		String orderName,
		long amount,
		String idempotencyKey
	) {
		Payment idempotentPayment = findIdempotentGoodsOrderPayment(
			user,
			goodsOrder,
			idempotencyKey
		);

		if (idempotentPayment != null) {
			return idempotentPayment;
		}

		Payment activePayment = findActiveGoodsOrderPayment(goodsOrder.getId()).orElse(null);

		if (activePayment != null) {
			return activePayment;
		}

		return createGoodsOrderReadyPayment(
			user,
			goodsOrder,
			orderName,
			amount,
			idempotencyKey
		);
	}

	// 같은 멱등성 키의 예약 결제 조회
	// 멱등성 키가 같더라도 user 또는 reservation이 다를 경우
	// 같은 요청의 재시도가 아니기 때문에 잘못된 요청으로 처리
	private Payment findIdempotentReservationPayment(
		User user,
		Reservation reservation,
		String idempotencyKey
	) {
		return paymentRepository.findByIdempotencyKey(idempotencyKey)
			.map(payment -> {
				validateSameReservationPayment(payment, user, reservation);
				return payment;
			})
			.orElse(null);
	}

	// 같은 멱등성 키의 주문 결제 조회
	// 멱등성 키가 같더라도 user 또는 goodsOrder가 다를 경우
	// 같은 요청의 재시도가 아니기 때문에 틀린 요청으로 처리한다.
	private Payment findIdempotentGoodsOrderPayment(
		User user,
		GoodsOrder goodsOrder,
		String idempotencyKey
	) {
		return paymentRepository.findByIdempotencyKey(idempotencyKey)
			.map(payment -> {
				validateSameGoodsOrderPayment(payment, user, goodsOrder);
				return payment;
			})
			.orElse(null);
	}

	// 같은 예약에 이미 활성 결제가 있는지 조회
	// READY 생성 전 중복 생성을 1차적으로 막는다
	private Optional<Payment> findActiveReservationPayment(Long reservationId) {
		return paymentRepository.findFirstByReservationIdAndPaymentTypeAndStatusInOrderByIdDesc(
			reservationId,
			PaymentType.POPUP,
			ACTIVE_PAYMENT_STATUSES
		);
	}

	// 같은 굿즈 주문에 이미 결제가 존재하는지 조회
	private Optional<Payment> findActiveGoodsOrderPayment(Long goodsOrderId) {
		return paymentRepository.findFirstByGoodsOrderIdAndPaymentTypeAndStatusInOrderByIdDesc(
			goodsOrderId,
			PaymentType.GOODS,
			ACTIVE_PAYMENT_STATUSES
		);
	}

	// 예약 결제용 READY 결제를 새로 생성한다.
	// 활성 결제 유니크 제약 위반 시 다른 트랜잭션이 먼저 생성한 결제를 다시 조회해 반환
	private Payment createReservationReadyPayment(
		User user,
		Reservation reservation,
		String orderName,
		long amount,
		String idempotencyKey
	) {
		Payment payment = Payment.createReadyReservationPayment(
			user,
			reservation,
			UUID.randomUUID().toString(),
			orderName,
			amount,
			idempotencyKey
		);

		try {
			return paymentRepository.saveAndFlush(payment);
		} catch (DataIntegrityViolationException exception) {
			return findActiveReservationPayment(reservation.getId())
				.orElseThrow(() -> exception);
		}
	}

	// 굿즈 주문 결제용 READY 결제를 새로 생성한다.
	// 활성 결제 유니크 제약 위반 시 다른 트랜잭션이 먼저 생성한 결제를 다시 조회해 반환
	private Payment createGoodsOrderReadyPayment(
		User user,
		GoodsOrder goodsOrder,
		String orderName,
		long amount,
		String idempotencyKey
	) {
		Payment payment = Payment.createReadyGoodsOrderPayment(
			user,
			goodsOrder,
			UUID.randomUUID().toString(),
			orderName,
			amount,
			idempotencyKey
		);

		try {
			return paymentRepository.saveAndFlush(payment);
		} catch (DataIntegrityViolationException exception) {
			return findActiveGoodsOrderPayment(goodsOrder.getId())
				.orElseThrow(() -> exception);
		}
	}

	// 멱등성 키로 찾은 결제가 현재 예약 결제 요청과 같은 요청인지 검증
	private void validateSameReservationPayment(
		Payment payment,
		User user,
		Reservation reservation
	) {
		if (!payment.getUser().getId().equals(user.getId())
			|| payment.getPaymentType() != PaymentType.POPUP
			|| payment.getReservation() == null
			|| !payment.getReservation().getId().equals(reservation.getId())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
	}

	// 멱등성 키로 찾은 결제가 현재 굿즈 주문 결제 요청과 같은 요청인지 검증
	private void validateSameGoodsOrderPayment(
		Payment payment,
		User user,
		GoodsOrder goodsOrder
	) {
		if (!payment.getUser().getId().equals(user.getId())
			|| payment.getPaymentType() != PaymentType.GOODS
			|| payment.getGoodsOrder() == null
			|| !payment.getGoodsOrder().getId().equals(goodsOrder.getId())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
	}
}
