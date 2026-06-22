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
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.repository.PaymentRepository;
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
		PaymentConfirmRequest request = request(1000L);
		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));

		assertBusinessException(
			() -> paymentTransactionService.complete(request, APPROVED_AT),
			ErrorCode.RESERVATION_PAYMENT_EXPIRED
		);
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
