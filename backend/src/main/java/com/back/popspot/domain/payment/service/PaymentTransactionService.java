package com.back.popspot.domain.payment.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

	private final PaymentRepository paymentRepository;

	@Transactional(readOnly = true)
	public Optional<PaymentConfirmResponse> prepare(PaymentConfirmRequest request) {
		Payment payment = getPayment(request.orderId());
		validatePayment(payment, request);

		if (payment.isPaid()) {
			validatePaymentKey(payment, request.paymentKey());
			return Optional.of(PaymentConfirmResponse.from(payment));
		}

		return Optional.empty();
	}

	@Transactional
	public PaymentConfirmResponse complete(PaymentConfirmRequest request, LocalDateTime approvedAt) {
		Payment payment = getPayment(request.orderId());
		validatePayment(payment, request);

		if (payment.isPaid()) {
			validatePaymentKey(payment, request.paymentKey());
			return PaymentConfirmResponse.from(payment);
		}

		payment.complete(request.paymentKey(), approvedAt);
		return PaymentConfirmResponse.from(payment);
	}

	private Payment getPayment(String orderId) {
		return paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
	}

	private void validatePayment(Payment payment, PaymentConfirmRequest request) {
		validateAmount(payment, request.amount());
		if (payment.isPaid()) {
			return;
		}
		if (!payment.isReady()) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_NOT_ALLOWED_STATUS);
		}
		validateReservationPayment(payment);
	}

	private void validateAmount(Payment payment, long requestAmount) {
		if (payment.getAmount() != requestAmount) {
			throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
		}
	}

	private void validatePaymentKey(Payment payment, String requestPaymentKey) {
		if (!requestPaymentKey.equals(payment.getPaymentKey())) {
			throw new BusinessException(ErrorCode.PAYMENT_KEY_MISMATCH);
		}
	}

	private void validateReservationPayment(Payment payment) {
		Reservation reservation = payment.getReservation();
		if (reservation == null) {
			return;
		}

		if (reservation.getStatus() != ReservationStatus.HELD) {
			throw new BusinessException(ErrorCode.RESERVATION_PAYMENT_NOT_ALLOWED_STATUS);
		}

		LocalDateTime heldUntil = reservation.getHeldUntil();
		if (heldUntil == null || !heldUntil.isAfter(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.RESERVATION_PAYMENT_EXPIRED);
		}
	}
}
