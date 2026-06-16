package com.back.popspot.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.payment.client.TossPaymentsClient;
import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.reservation.entity.Reservation;
import com.back.popspot.domain.reservation.entity.ReservationStatus;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private TossPaymentsClient tossPaymentsClient;

	@InjectMocks
	private PaymentService paymentService;

	@Test
	@DisplayName("READY 결제를 승인하고 결제와 예약 상태를 완료 처리한다")
	void confirmReadyReservationPayment() throws Exception {
		Reservation reservation = reservation(1L, ReservationStatus.HELD);
		Payment payment = readyReservationPayment(reservation);
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 1000L);
		JsonNode tossResponse = JsonMapper.builder().build()
			.readTree("{\"status\":\"DONE\",\"approvedAt\":\"2026-06-16T10:00:00+09:00\"}");

		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));
		given(tossPaymentsClient.confirm(request)).willReturn(tossResponse);

		PaymentConfirmResponse response = paymentService.confirm(request);

		assertThat(response.paymentId()).isEqualTo(10L);
		assertThat(response.paymentType()).isEqualTo(PaymentType.POPUP);
		assertThat(response.orderId()).isEqualTo("order-id");
		assertThat(response.paymentKey()).isEqualTo("payment-key");
		assertThat(response.amount()).isEqualTo(1000L);
		assertThat(response.status()).isEqualTo(Payment.DONE_STATUS);
		assertThat(response.approvedAt()).isEqualTo(LocalDateTime.of(2026, 6, 16, 10, 0));
		assertThat(payment.getStatus()).isEqualTo(Payment.DONE_STATUS);
		assertThat(payment.getPaymentKey()).isEqualTo("payment-key");
		assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
	}

	@Test
	@DisplayName("요청 금액이 결제 금액과 다르면 승인하지 않는다")
	void rejectAmountMismatch() {
		Payment payment = readyPayment();
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 2000L);

		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentService.confirm(request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

		verify(tossPaymentsClient, never()).confirm(request);
	}

	@Test
	@DisplayName("이미 승인된 결제는 같은 결제 키 요청에 기존 결과를 반환한다")
	void returnExistingDonePayment() {
		Payment payment = readyPayment();
		payment.complete("payment-key", LocalDateTime.of(2026, 6, 16, 10, 0));
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 1000L);

		given(paymentRepository.findByOrderId("order-id")).willReturn(Optional.of(payment));

		PaymentConfirmResponse response = paymentService.confirm(request);

		assertThat(response.status()).isEqualTo(Payment.DONE_STATUS);
		assertThat(response.paymentKey()).isEqualTo("payment-key");
		verify(tossPaymentsClient, never()).confirm(request);
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

	private Reservation reservation(Long id, ReservationStatus status) {
		Reservation reservation = new Reservation();
		ReflectionTestUtils.setField(reservation, "id", id);
		ReflectionTestUtils.setField(reservation, "status", status);
		return reservation;
	}
}
