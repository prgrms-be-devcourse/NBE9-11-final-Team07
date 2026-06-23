package com.back.popspot.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.dto.PaymentCancelCommand;
import com.back.popspot.domain.payment.dto.PaymentCancelPreparation;
import com.back.popspot.domain.payment.dto.PaymentCancelRequest;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentRefund;
import com.back.popspot.domain.payment.entity.PaymentRefundStatus;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.payment.repository.PaymentRefundRepository;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {

	private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 6, 16, 10, 0);

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentRefundRepository paymentRefundRepository;

	@InjectMocks
	private PaymentTransactionService paymentTransactionService;

	@Test
	@DisplayName("READY 결제는 토스 승인이 필요한 상태로 확인한다")
	void prepareReadyPayment() {
		Payment payment = readyPayment();
		PaymentConfirmRequest request = request(1000L);
		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));

		Optional<PaymentConfirmResponse> result = paymentTransactionService.prepare(request);

		assertThat(result).isEmpty();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
	}

	@Test
	@DisplayName("토스 승인 후 결제와 예약 상태를 완료 처리한다")
	void completeReadyReservationPayment() {
		Reservation reservation = reservation(ReservationStatus.HELD, LocalDateTime.now().plusMinutes(5));
		Payment payment = readyReservationPayment(reservation);
		PaymentConfirmRequest request = request(1000L);
		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));

		PaymentConfirmResponse response = paymentTransactionService.complete(request, APPROVED_AT);

		assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
		assertThat(response.paymentKey()).isEqualTo("payment-key");
		assertThat(response.approvedAt()).isEqualTo(APPROVED_AT);
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
	}

	@Test
	@DisplayName("이미 승인된 결제는 같은 결제 키 요청에 기존 결과를 반환한다")
	void returnExistingDonePayment() {
		Payment payment = readyPayment();
		payment.complete("payment-key", APPROVED_AT);
		PaymentConfirmRequest request = request(1000L);
		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));

		Optional<PaymentConfirmResponse> result = paymentTransactionService.prepare(request);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().status()).isEqualTo(PaymentStatus.PAID);
	}

	@Test
	@DisplayName("완료 처리 직전에 이미 승인된 결제도 기존 결과를 반환한다")
	void returnPaymentCompletedBetweenTransactions() {
		Payment payment = readyPayment();
		payment.complete("payment-key", APPROVED_AT);
		PaymentConfirmRequest request = request(1000L);
		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));

		PaymentConfirmResponse response = paymentTransactionService.complete(request, APPROVED_AT.plusSeconds(1));

		assertThat(response.approvedAt()).isEqualTo(APPROVED_AT);
	}

	@Test
	@DisplayName("요청 금액이 결제 금액과 다르면 승인하지 않는다")
	void rejectAmountMismatch() {
		Payment payment = readyPayment();
		PaymentConfirmRequest request = request(2000L);
		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));

		assertBusinessException(
			() -> paymentTransactionService.prepare(request),
			ErrorCode.PAYMENT_AMOUNT_MISMATCH
		);
	}

	@Test
	@DisplayName("토스 승인 중 예약 선점 시간이 만료되면 완료 처리하지 않는다")
	void rejectReservationExpiredBeforeCompletion() {
		Reservation reservation = reservation(ReservationStatus.HELD, LocalDateTime.now().minusSeconds(1));
		Payment payment = readyReservationPayment(reservation);
		payment.beginConfirmation();
		PaymentConfirmRequest request = request(1000L);
		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));

		assertBusinessException(
			() -> paymentTransactionService.complete(request, APPROVED_AT),
			ErrorCode.RESERVATION_PAYMENT_EXPIRED
		);
	}

	@Test
	@DisplayName("주문 확정 실패 후 결제 보상 요청을 생성한다")
	void preparePaymentCompensation() {
		Payment payment = readyPayment();
		payment.beginConfirmation();
		PaymentConfirmRequest request = request(1000L);
		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));
		given(paymentRefundRepository.findByIdempotencyKey("confirm-compensation:order-id"))
			.willReturn(Optional.empty());

		PaymentCancelCommand command = paymentTransactionService.prepareCompensation(request, "주문 확정 실패");

		assertThat(command.paymentKey()).isEqualTo("payment-key");
		assertThat(command.idempotencyKey()).isEqualTo("confirm-compensation:order-id");
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPENSATING);
	}

	@Test
	@DisplayName("보상 취소가 실패하면 결제를 재처리 대상 상태로 남긴다")
	void failPaymentCompensation() {
		Payment payment = readyPayment();
		payment.beginConfirmation();
		payment.beginCompensation("payment-key");
		PaymentRefund refund = refund(payment, PaymentRefundStatus.REQUESTED);
		PaymentCancelCommand command = new PaymentCancelCommand(
			10L, "payment-key", "order-id", 1000L, "주문 확정 실패", "cancel-key"
		);
		given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
		given(paymentRefundRepository.findByIdempotencyKey("cancel-key")).willReturn(Optional.of(refund));

		paymentTransactionService.failCompensation(command);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPENSATION_FAILED);
		assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.FAILED);
	}

	@Test
	@DisplayName("예약 상태가 HELD가 아니면 결제를 승인하지 않는다")
	void rejectNotHeldReservationPayment() {
		Reservation reservation = reservation(ReservationStatus.CANCELED, LocalDateTime.now().plusMinutes(5));
		Payment payment = readyReservationPayment(reservation);
		PaymentConfirmRequest request = request(1000L);
		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));

		assertBusinessException(
			() -> paymentTransactionService.prepare(request),
			ErrorCode.RESERVATION_PAYMENT_NOT_ALLOWED_STATUS
		);
	}

	@Test
	@DisplayName("PAID 결제의 전액 취소 요청을 생성한다")
	void preparePaidPaymentCancel() {
		Payment payment = paidPayment();
		PaymentCancelRequest request = new PaymentCancelRequest("구매자 변심", "cancel-key");
		given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
		given(paymentRefundRepository.findByIdempotencyKey("cancel-key")).willReturn(Optional.empty());
		given(paymentRefundRepository.findFirstByPaymentIdOrderByIdDesc(10L)).willReturn(Optional.empty());

		PaymentCancelPreparation preparation = paymentTransactionService.prepareCancel(10L, 1L, request);

		assertThat(preparation.isCompleted()).isFalse();
		assertThat(preparation.command().paymentKey()).isEqualTo("payment-key");
		assertThat(preparation.command().amount()).isEqualTo(1000L);
	}

	@Test
	@DisplayName("실패한 환불 기록이 있으면 새로운 멱등키로 취소를 재시도한다")
	void retryFailedPaymentCancelWithNewIdempotencyKey() {
		Payment payment = paidPayment();
		PaymentRefund failedRefund = refund(payment, PaymentRefundStatus.FAILED);
		PaymentCancelRequest request = new PaymentCancelRequest("재시도", "new-cancel-key");
		given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
		given(paymentRefundRepository.findByIdempotencyKey("new-cancel-key")).willReturn(Optional.empty());
		given(paymentRefundRepository.findFirstByPaymentIdOrderByIdDesc(10L))
			.willReturn(Optional.of(failedRefund));

		PaymentCancelPreparation preparation = paymentTransactionService.prepareCancel(10L, 1L, request);

		assertThat(preparation.isCompleted()).isFalse();
		assertThat(preparation.command().idempotencyKey()).isEqualTo("new-cancel-key");
		assertThat(preparation.command().cancelReason()).isEqualTo("재시도");
	}

	@Test
	@DisplayName("진행 중인 환불 기록이 있으면 새로운 멱등키 취소를 거부한다")
	void rejectNewCancelWhileRefundIsRequested() {
		Payment payment = paidPayment();
		PaymentRefund requestedRefund = refund(payment, PaymentRefundStatus.REQUESTED);
		PaymentCancelRequest request = new PaymentCancelRequest("중복 요청", "new-cancel-key");
		given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
		given(paymentRefundRepository.findByIdempotencyKey("new-cancel-key")).willReturn(Optional.empty());
		given(paymentRefundRepository.findFirstByPaymentIdOrderByIdDesc(10L))
			.willReturn(Optional.of(requestedRefund));

		assertBusinessException(
			() -> paymentTransactionService.prepareCancel(10L, 1L, request),
			ErrorCode.PAYMENT_CANCEL_ALREADY_REQUESTED
		);
	}

	@Test
	@DisplayName("토스 취소 완료 후 결제와 환불 상태를 변경한다")
	void completePaymentCancel() {
		Payment payment = paidPayment();
		PaymentRefund refund = refund(payment, PaymentRefundStatus.REQUESTED);
		PaymentCancelCommand command = cancelCommand();
		LocalDateTime canceledAt = LocalDateTime.of(2026, 6, 16, 11, 0);
		given(paymentRefundRepository.findByIdempotencyKey("cancel-key")).willReturn(Optional.of(refund));
		given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));

		paymentTransactionService.completeCancel(command, "transaction-key", canceledAt);

		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.DONE);
		assertThat(refund.getTransactionKey()).isEqualTo("transaction-key");
		assertThat(refund.getCompletedAt()).isEqualTo(canceledAt);
	}

	@Test
	@DisplayName("본인 결제가 아니면 취소를 거부한다")
	void rejectOtherUsersPaymentCancel() {
		Payment payment = paidPayment();
		given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));

		assertBusinessException(
			() -> paymentTransactionService.prepareCancel(
				10L,
				2L,
				new PaymentCancelRequest("구매자 변심", "cancel-key")
			),
			ErrorCode.FORBIDDEN
		);
	}

	private void assertBusinessException(Runnable action, ErrorCode errorCode) {
		assertThatThrownBy(action::run)
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(errorCode);
	}

	private PaymentConfirmRequest request(long amount) {
		return new PaymentConfirmRequest("payment-key", "order-id", amount);
	}

	private Payment readyPayment() {
		User user = User.create("user@example.com", "사용자");
		Payment payment = Payment.createReady(
			user,
			PaymentType.GOODS,
			"order-id",
			"굿즈 결제",
			1000L,
			"idempotency-key"
		);
		ReflectionTestUtils.setField(payment, "id", 10L);
		return payment;
	}

	private Payment paidPayment() {
		Payment payment = readyPayment();
		ReflectionTestUtils.setField(payment.getUser(), "id", 1L);
		payment.complete("payment-key", APPROVED_AT);
		return payment;
	}

	private PaymentRefund refund(Payment payment, PaymentRefundStatus status) {
		PaymentRefund refund = PaymentRefund.request(
			payment,
			payment.getUser(),
			payment.getAmount(),
			"구매자 변심",
			"cancel-key"
		);
		ReflectionTestUtils.setField(refund, "id", 20L);
		ReflectionTestUtils.setField(refund, "status", status);
		return refund;
	}

	private PaymentCancelCommand cancelCommand() {
		return new PaymentCancelCommand(
			10L,
			"payment-key",
			"order-id",
			1000L,
			"구매자 변심",
			"cancel-key"
		);
	}

	private Payment readyReservationPayment(Reservation reservation) {
		User user = User.create("user@example.com", "사용자");
		Payment payment = Payment.createReadyReservationPayment(
			user,
			reservation,
			"order-id",
			"예약 결제",
			1000L,
			"idempotency-key"
		);
		ReflectionTestUtils.setField(payment, "id", 10L);
		return payment;
	}

	private Reservation reservation(ReservationStatus status, LocalDateTime heldUntil) {
		Reservation reservation = new Reservation();
		ReflectionTestUtils.setField(reservation, "id", 1L);
		ReflectionTestUtils.setField(reservation, "status", status);
		ReflectionTestUtils.setField(reservation, "heldUntil", heldUntil);
		return reservation;
	}
}
