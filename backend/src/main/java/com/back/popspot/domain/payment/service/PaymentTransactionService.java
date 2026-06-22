package com.back.popspot.domain.payment.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.dto.PaymentCancelCommand;
import com.back.popspot.domain.payment.dto.PaymentCancelPreparation;
import com.back.popspot.domain.payment.dto.PaymentCancelRequest;
import com.back.popspot.domain.payment.dto.PaymentCancelResponse;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentRefund;
import com.back.popspot.domain.payment.entity.PaymentRefundStatus;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.payment.repository.PaymentRefundRepository;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

	private final PaymentRepository paymentRepository;
	private final PaymentRefundRepository paymentRefundRepository;

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

	@Transactional
	public PaymentCancelPreparation prepareCancel(Long paymentId, Long userId, PaymentCancelRequest request) {
		Payment payment = getPayment(paymentId);
		validateOwner(payment, userId);

		Optional<PaymentRefund> sameRequest = paymentRefundRepository.findByIdempotencyKey(request.idempotencyKey());
		if (sameRequest.isPresent()) {
			PaymentRefund refund = sameRequest.get();
			validateSameCancelRequest(refund, payment, userId, request);
			if (refund.getStatus() == PaymentRefundStatus.DONE) {
				return PaymentCancelPreparation.completed(PaymentCancelResponse.from(refund));
			}
			validateCancelable(payment);
			refund.retry();
			return PaymentCancelPreparation.required(toCommand(payment, request));
		}

		if (payment.isCanceled()) {
			PaymentRefund refund = paymentRefundRepository.findFirstByPaymentIdOrderByIdDesc(paymentId)
				.filter(existing -> existing.getStatus() == PaymentRefundStatus.DONE)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED_STATUS));
			return PaymentCancelPreparation.completed(PaymentCancelResponse.from(refund));
		}

		validateCancelable(payment);
		paymentRefundRepository.findFirstByPaymentIdOrderByIdDesc(paymentId)
			.filter(refund -> refund.getStatus() == PaymentRefundStatus.REQUESTED)
			.ifPresent(refund -> {
				throw new BusinessException(ErrorCode.PAYMENT_CANCEL_ALREADY_REQUESTED);
			});

		PaymentRefund refund = PaymentRefund.request(
			payment,
			payment.getUser(),
			payment.getAmount(),
			request.cancelReason(),
			request.idempotencyKey()
		);
		paymentRefundRepository.save(refund);

		return PaymentCancelPreparation.required(toCommand(payment, request));
	}

	@Transactional
	public PaymentCancelResponse completeCancel(
		PaymentCancelCommand command,
		String transactionKey,
		LocalDateTime canceledAt
	) {
		PaymentRefund refund = getRefund(command.idempotencyKey());
		if (refund.getStatus() == PaymentRefundStatus.DONE) {
			return PaymentCancelResponse.from(refund);
		}

		Payment payment = getPayment(command.paymentId());
		if (!payment.isPaid()) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED_STATUS);
		}

		payment.cancel();
		refund.complete(transactionKey, canceledAt);
		return PaymentCancelResponse.from(refund);
	}

	@Transactional
	public void failCancel(String idempotencyKey) {
		PaymentRefund refund = getRefund(idempotencyKey);
		if (refund.getStatus() == PaymentRefundStatus.REQUESTED) {
			refund.fail();
		}
	}

	private Payment getPayment(Long paymentId) {
		return paymentRepository.findById(paymentId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
	}

	private PaymentRefund getRefund(String idempotencyKey) {
		return paymentRefundRepository.findByIdempotencyKey(idempotencyKey)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
	}

	private void validateOwner(Payment payment, Long userId) {
		if (!payment.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}

	private void validateCancelable(Payment payment) {
		if (!payment.isPaid() || payment.getPaymentKey() == null) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED_STATUS);
		}
	}

	private void validateSameCancelRequest(
		PaymentRefund refund,
		Payment payment,
		Long userId,
		PaymentCancelRequest request
	) {
		if (!refund.getPayment().getId().equals(payment.getId())
			|| !refund.getUser().getId().equals(userId)
			|| !refund.getReason().equals(request.cancelReason())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
	}

	private PaymentCancelCommand toCommand(Payment payment, PaymentCancelRequest request) {
		return new PaymentCancelCommand(
			payment.getId(),
			payment.getPaymentKey(),
			payment.getOrderId(),
			payment.getAmount(),
			request.cancelReason(),
			request.idempotencyKey()
		);
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
