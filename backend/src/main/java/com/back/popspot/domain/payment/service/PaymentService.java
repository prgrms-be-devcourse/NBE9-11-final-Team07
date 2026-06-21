package com.back.popspot.domain.payment.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.payment.client.TossPaymentsClient;
import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final TossPaymentsClient tossPaymentsClient;

	@Transactional
	public PaymentConfirmResponse confirm(PaymentConfirmRequest request) {
		Payment payment = paymentRepository.findByOrderId(request.orderId())
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

		validateAmount(payment, request.amount());

		if (payment.isPaid()) {
			validatePaymentKey(payment, request.paymentKey());
			return PaymentConfirmResponse.from(payment);
		}

		if (!payment.isReady()) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_NOT_ALLOWED_STATUS);
		}

		validateReservationPayment(payment);

		JsonNode tossResponse = tossPaymentsClient.confirm(request);
		validateTossConfirmResponse(tossResponse, request);
		payment.complete(
			request.paymentKey(),
			extractApprovedAt(tossResponse)
		);

		return PaymentConfirmResponse.from(payment);
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

	private void validateTossConfirmResponse(JsonNode tossResponse, PaymentConfirmRequest request) {
		if (!"DONE".equals(tossResponse.path("status").asString(null))
			|| !request.paymentKey().equals(tossResponse.path("paymentKey").asString(null))
			|| !request.orderId().equals(tossResponse.path("orderId").asString(null))
			|| !String.valueOf(request.amount()).equals(tossResponse.path("totalAmount").asString(null))
			|| tossResponse.path("approvedAt").asString(null) == null
			|| tossResponse.path("approvedAt").asString(null).isBlank()) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_RESPONSE_MISMATCH);
		}
	}

	private LocalDateTime extractApprovedAt(JsonNode tossResponse) {
		String approvedAt = tossResponse.path("approvedAt").asString(null);
		try {
			return OffsetDateTime.parse(approvedAt).toLocalDateTime();
		} catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_RESPONSE_MISMATCH);
		}
	}
}
