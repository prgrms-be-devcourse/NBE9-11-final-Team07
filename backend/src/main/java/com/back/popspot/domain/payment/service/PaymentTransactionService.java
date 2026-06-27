package com.back.popspot.domain.payment.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.payment.dto.PaymentCancelCommand;
import com.back.popspot.domain.payment.dto.PaymentCancelPreparation;
import com.back.popspot.domain.payment.dto.PaymentCancelRequest;
import com.back.popspot.domain.payment.dto.PaymentCancelResponse;
import com.back.popspot.domain.payment.dto.PaymentConfirmPreparation;
import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentRefund;
import com.back.popspot.domain.payment.entity.PaymentRefundStatus;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.repository.PaymentRefundRepository;
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
	private final PaymentRefundRepository paymentRefundRepository;

	// 결제 승인 전 사전 검증과 승인 진행 상태 전환
	@Transactional
	public PaymentConfirmPreparation prepare(PaymentConfirmRequest request) {
		Payment payment = getPayment(request.orderId());
		validateAmount(payment, request.amount());

		// 1. 가격 검증
		if (payment.isPaid()) {
			validatePaymentKey(payment, request.paymentKey());
			return PaymentConfirmPreparation.completed(PaymentConfirmResponse.from(payment));
		}

		// 2. PaymentKey 검증
		if (payment.hasDifferentPaymentKey(request.paymentKey())) {
			throw new BusinessException(ErrorCode.PAYMENT_KEY_MISMATCH);
		}

		// 3. 상태값 READY 검증
		if (payment.isReady()) {
			validateReservationPayment(payment);

			// READY면 CONFIRMING으로 상태 변환
			int updated = paymentRepository.beginConfirmationIfReady(
				request.orderId(),
				request.amount(),
				request.paymentKey(),
				request.idempotencyKey(),
				LocalDateTime.now(),
				PaymentStatus.READY,
				PaymentStatus.CONFIRMING
			);

			if (updated == 1) {
				return PaymentConfirmPreparation.required();
			}

			Payment refreshed = getPayment(request.orderId());
			return handleAlreadyStartedConfirmation(refreshed, request);
		}

		if (payment.isConfirming()) {
			return handleAlreadyStartedConfirmation(payment, request);
		}

		throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_NOT_ALLOWED_STATUS);
	}

	// 토스 승인 성공 후 결제와 주문을 완료 처리
	@Transactional
	public PaymentConfirmResponse complete(PaymentConfirmRequest request, LocalDateTime approvedAt) {
		Payment payment = getPayment(request.orderId());
		validatePayment(payment, request);

		if (payment.isPaid()) {
			validatePaymentKey(payment, request.paymentKey());
			return PaymentConfirmResponse.from(payment);
		}

		validateReservationPayment(payment);
		payment.complete(request.paymentKey(), approvedAt);
		return PaymentConfirmResponse.from(payment);
	}

	// 이미 시작된 결제 승인 요청의 처리 방향을 결정
	private PaymentConfirmPreparation handleAlreadyStartedConfirmation(Payment payment, PaymentConfirmRequest request) {
		if (payment.isPaid()) {
			validatePaymentKey(payment, request.paymentKey());
			return PaymentConfirmPreparation.completed(PaymentConfirmResponse.from(payment));
		}

		if (!payment.isConfirming()) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_NOT_ALLOWED_STATUS);
		}

		if (!payment.hasSameConfirmRequest(request.paymentKey(), request.idempotencyKey())) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_ALREADY_REQUESTED);
		}

		throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_IN_PROGRESS);
	}

	// 승인 후 주문 확정 실패 시 보상 취소 명령을 준비
	@Transactional
	public PaymentCancelCommand prepareCompensation(PaymentConfirmRequest request, String reason) {
		Payment payment = getPayment(request.orderId());
		if (!payment.isConfirming()) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_NOT_ALLOWED_STATUS);
		}

		String idempotencyKey = compensationIdempotencyKey(payment);
		Optional<PaymentRefund> existing = paymentRefundRepository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			existing.get().retry();
		} else {
			paymentRefundRepository.save(PaymentRefund.request(
				payment,
				payment.getUser(),
				payment.getAmount(),
				reason,
				idempotencyKey
			));
		}

		payment.beginCompensation(request.paymentKey());
		return new PaymentCancelCommand(
			payment.getId(),
			request.paymentKey(),
			payment.getOrderId(),
			payment.getAmount(),
			reason,
			idempotencyKey
		);
	}

	// 보상 취소 성공 결과를 결제와 환불에 반영
	@Transactional
	public void completeCompensation(
		PaymentCancelCommand command,
		String transactionKey,
		LocalDateTime canceledAt
	) {
		Payment payment = getPayment(command.paymentId());
		PaymentRefund refund = getRefund(command.idempotencyKey());
		payment.cancel();
		refund.complete(transactionKey, canceledAt);
	}

	// 보상 취소 실패 결과를 결제와 환불에 반영
	@Transactional
	public void failCompensation(PaymentCancelCommand command) {
		Payment payment = getPayment(command.paymentId());
		PaymentRefund refund = getRefund(command.idempotencyKey());
		payment.failCompensation();
		refund.fail();
	}

	// 실패한 보상 취소 재시도 명령 목록을 준비
	@Transactional
	public List<PaymentCancelCommand> prepareFailedCompensations() {
		return paymentRefundRepository.findTop20ByStatusOrderByIdAsc(PaymentRefundStatus.FAILED)
			.stream()
			.filter(refund -> refund.getPayment().getStatus() == PaymentStatus.COMPENSATION_FAILED)
			.map(refund -> {
				Payment payment = refund.getPayment();
				payment.retryCompensation();
				refund.retry();
				return new PaymentCancelCommand(
					payment.getId(),
					payment.getPaymentKey(),
					payment.getOrderId(),
					payment.getAmount(),
					refund.getReason(),
					refund.getIdempotencyKey()
				);
			})
			.toList();
	}

	// 결제 취소 전 소유자와 멱등성, 취소 가능 상태를 검증
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
			validateRetryableCancel(payment);
			payment.retryCancel();
			refund.retry();
			return PaymentCancelPreparation.required(toCommand(payment, request));
		}

		if (payment.isCanceled()) {
			PaymentRefund refund = paymentRefundRepository.findFirstByPaymentIdOrderByIdDesc(paymentId)
				.filter(existing -> existing.getStatus() == PaymentRefundStatus.DONE)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED_STATUS));
			return PaymentCancelPreparation.completed(PaymentCancelResponse.from(refund));
		}

		paymentRefundRepository.findFirstByPaymentIdOrderByIdDesc(paymentId)
			.filter(refund -> refund.getStatus() == PaymentRefundStatus.REQUESTED)
			.ifPresent(refund -> {
				throw new BusinessException(ErrorCode.PAYMENT_CANCEL_ALREADY_REQUESTED);
			});
		validateCancelable(payment);

		PaymentRefund refund = PaymentRefund.request(
			payment,
			payment.getUser(),
			payment.getAmount(),
			request.cancelReason(),
			request.idempotencyKey()
		);
		paymentRefundRepository.save(refund);
		payment.beginCancel();

		return PaymentCancelPreparation.required(toCommand(payment, request));
	}

	// 토스 취소 성공 후 결제와 환불을 완료 처리
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
		if (!payment.isPaid() && !payment.isCanceling() && !payment.isCancelFailed()) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED_STATUS);
		}

		payment.cancel();
		refund.complete(transactionKey, canceledAt);
		return PaymentCancelResponse.from(refund);
	}

	// 토스 취소 실패 시 환불 요청을 실패 처리
	@Transactional
	public void failCancel(String idempotencyKey) {
		PaymentRefund refund = getRefund(idempotencyKey);
		if (refund.getStatus() == PaymentRefundStatus.REQUESTED) {
			refund.getPayment().failCancel();
			refund.fail();
		}
	}

	// 실패한 일반 취소 재시도 명령 목록을 준비
	@Transactional
	public List<PaymentCancelCommand> prepareFailedCancels() {
		return paymentRefundRepository.findTop20ByStatusOrderByIdAsc(PaymentRefundStatus.FAILED)
			.stream()
			.filter(refund -> refund.getPayment().getStatus() == PaymentStatus.CANCEL_FAILED)
			.map(refund -> {
				Payment payment = refund.getPayment();
				payment.retryCancel();
				refund.retry();
				return new PaymentCancelCommand(
					payment.getId(),
					payment.getPaymentKey(),
					payment.getOrderId(),
					payment.getAmount(),
					refund.getReason(),
					refund.getIdempotencyKey()
				);
			})
			.toList();
	}

	// 결제 ID로 결제를 조회
	private Payment getPayment(Long paymentId) {
		return paymentRepository.findById(paymentId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
	}

	// 멱등성 키로 환불 요청을 조회
	private PaymentRefund getRefund(String idempotencyKey) {
		return paymentRefundRepository.findByIdempotencyKey(idempotencyKey)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
	}

	// 결제 소유자와 요청 사용자가 일치하는지 검증
	private void validateOwner(Payment payment, Long userId) {
		if (!payment.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}

	// 결제가 취소 가능한 상태인지 검증
	private void validateCancelable(Payment payment) {
		if ((!payment.isPaid() && !payment.isCancelFailed()) || payment.getPaymentKey() == null) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED_STATUS);
		}
	}

	// 결제가 취소 재시도 가능한 상태인지 검증
	private void validateRetryableCancel(Payment payment) {
		if ((!payment.isPaid() && !payment.isCanceling() && !payment.isCancelFailed())
			|| payment.getPaymentKey() == null) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED_STATUS);
		}
	}

	// 같은 멱등성 키의 취소 요청 내용이 기존 요청과 일치하는지 검증
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

	// 결제와 취소 요청으로 토스 취소 명령을 생성
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

	// 주문 번호로 결제를 조회
	private Payment getPayment(String orderId) {
		return paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
	}

	// 결제 승인 요청의 금액과 상태를 검증
	private void validatePayment(Payment payment, PaymentConfirmRequest request) {
		validateAmount(payment, request.amount());
		if (payment.isPaid()) {
			return;
		}
		if (!payment.isReady() && !payment.isConfirming()) {
			throw new BusinessException(ErrorCode.PAYMENT_CONFIRM_NOT_ALLOWED_STATUS);
		}
		if (payment.isReady()) {
			validateReservationPayment(payment);
		}
	}

	// 보상 취소용 멱등성 키를 생성
	private String compensationIdempotencyKey(Payment payment) {
		return "confirm-compensation:" + payment.getOrderId();
	}

	// 요청 금액과 결제 금액이 일치하는지 검증
	private void validateAmount(Payment payment, long requestAmount) {
		if (payment.getAmount() != requestAmount) {
			throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
		}
	}

	// 요청 결제 키와 저장된 결제 키가 일치하는지 검증
	private void validatePaymentKey(Payment payment, String requestPaymentKey) {
		if (!requestPaymentKey.equals(payment.getPaymentKey())) {
			throw new BusinessException(ErrorCode.PAYMENT_KEY_MISMATCH);
		}
	}

	// 예약 결제의 예약 상태와 점유 만료 시간을 검증
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
