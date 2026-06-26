package com.back.popspot.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.client.RestClientException;

import com.back.popspot.domain.payment.client.TossPaymentsClient;
import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.dto.PaymentCancelCommand;
import com.back.popspot.domain.payment.dto.PaymentCancelPreparation;
import com.back.popspot.domain.payment.dto.PaymentCancelRequest;
import com.back.popspot.domain.payment.dto.PaymentCancelResponse;
import com.back.popspot.domain.payment.entity.PaymentRefundStatus;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	private static final LocalDateTime APPROVED_AT = LocalDateTime.of(2026, 6, 16, 10, 0);

	@Mock
	private PaymentTransactionService paymentTransactionService;

	@Mock
	private TossPaymentsClient tossPaymentsClient;

	@InjectMocks
	private PaymentService paymentService;

	@Test
	@DisplayName("READY 결제는 토스 승인 후 별도 트랜잭션에서 완료 처리한다")
	void confirmReadyPayment() throws Exception {
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 1000L);
		PaymentConfirmResponse expected = paidResponse();
		JsonNode tossResponse = tossResponse("payment-key", "order-id", 1000L);

		given(paymentTransactionService.prepare(request)).willReturn(Optional.empty());
		given(tossPaymentsClient.confirm(request)).willReturn(tossResponse);
		given(paymentTransactionService.complete(request, APPROVED_AT)).willReturn(expected);

		PaymentConfirmResponse response = paymentService.confirm(request);

		assertThat(response).isEqualTo(expected);
		verify(paymentTransactionService).complete(request, APPROVED_AT);
	}

	@Test
	@DisplayName("토스 승인 후 DB 커밋에 실패하면 재요청으로 결제를 완료한다")
	void recoverPaymentOnRetryAfterCommitFailure() throws Exception {
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 1000L);
		PaymentConfirmResponse expected = paidResponse();
		JsonNode tossResponse = tossResponse("payment-key", "order-id", 1000L);

		given(paymentTransactionService.prepare(request)).willReturn(Optional.empty());
		given(tossPaymentsClient.confirm(request)).willReturn(tossResponse);
		given(paymentTransactionService.complete(request, APPROVED_AT))
			.willThrow(new TransactionSystemException("commit failed"))
			.willReturn(expected);

		assertThatThrownBy(() -> paymentService.confirm(request))
			.isInstanceOf(TransactionSystemException.class);

		PaymentConfirmResponse response = paymentService.confirm(request);

		assertThat(response).isEqualTo(expected);
		verify(tossPaymentsClient, times(2)).confirm(request);
		verify(paymentTransactionService, times(2)).complete(request, APPROVED_AT);
	}

	@Test
	@DisplayName("이미 승인된 결제는 토스 API를 호출하지 않고 기존 결과를 반환한다")
	void returnExistingDonePayment() {
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 1000L);
		PaymentConfirmResponse expected = paidResponse();

		given(paymentTransactionService.prepare(request)).willReturn(Optional.of(expected));

		PaymentConfirmResponse response = paymentService.confirm(request);

		assertThat(response).isEqualTo(expected);
		verify(tossPaymentsClient, never()).confirm(request);
		verify(paymentTransactionService, never()).complete(any(), any());
	}

	@Test
	@DisplayName("토스 승인 응답이 요청 정보와 다르면 완료 트랜잭션을 실행하지 않는다")
	void rejectTossResponseMismatch() throws Exception {
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 1000L);
		JsonNode tossResponse = tossResponse("other-payment-key", "order-id", 1000L);

		given(paymentTransactionService.prepare(request)).willReturn(Optional.empty());
		given(tossPaymentsClient.confirm(request)).willReturn(tossResponse);

		assertThatThrownBy(() -> paymentService.confirm(request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PAYMENT_CONFIRM_RESPONSE_MISMATCH);

		verify(paymentTransactionService, never()).complete(any(), any());
	}

	@Test
	@DisplayName("승인 후 예약을 확정할 수 없으면 토스 결제를 보상 취소한다")
	void compensateWhenReservationCannotBeConfirmed() throws Exception {
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 1000L);
		PaymentCancelCommand command = cancelCommand();
		JsonNode tossResponse = tossResponse("payment-key", "order-id", 1000L);
		JsonNode cancelResponse = cancelResponse("CANCELED", "DONE", 1000L);

		given(paymentTransactionService.prepare(request)).willReturn(Optional.empty());
		given(tossPaymentsClient.confirm(request)).willReturn(tossResponse);
		given(paymentTransactionService.complete(request, APPROVED_AT))
			.willThrow(new BusinessException(ErrorCode.RESERVATION_PAYMENT_EXPIRED));
		given(paymentTransactionService.prepareCompensation(request, "결제 승인 후 주문 확정 실패"))
			.willReturn(command);
		given(tossPaymentsClient.cancel(command)).willReturn(cancelResponse);

		assertThatThrownBy(() -> paymentService.confirm(request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.RESERVATION_PAYMENT_EXPIRED);

		verify(paymentTransactionService).completeCompensation(
			command,
			"transaction-key",
			LocalDateTime.of(2026, 6, 16, 11, 0)
		);
	}

	@Test
	@DisplayName("보상 취소 호출이 실패하면 보상 실패 상태로 기록한다")
	void recordFailedCompensation() throws Exception {
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 1000L);
		PaymentCancelCommand command = cancelCommand();

		given(paymentTransactionService.prepare(request)).willReturn(Optional.empty());
		given(tossPaymentsClient.confirm(request)).willReturn(tossResponse("payment-key", "order-id", 1000L));
		given(paymentTransactionService.complete(request, APPROVED_AT))
			.willThrow(new BusinessException(ErrorCode.RESERVATION_PAYMENT_EXPIRED));
		given(paymentTransactionService.prepareCompensation(request, "결제 승인 후 주문 확정 실패"))
			.willReturn(command);
		given(tossPaymentsClient.cancel(command)).willThrow(new RestClientException("toss error"));

		assertThatThrownBy(() -> paymentService.confirm(request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.RESERVATION_PAYMENT_EXPIRED);

		verify(paymentTransactionService).failCompensation(command);
	}

	@Test
	@DisplayName("결제 전액 취소 성공 응답을 검증한 후 완료 처리한다")
	void cancelPaidPayment() throws Exception {
		PaymentCancelRequest request = new PaymentCancelRequest("구매자 변심", "cancel-key");
		PaymentCancelCommand command = cancelCommand();
		PaymentCancelResponse expected = canceledResponse();
		JsonNode tossResponse = cancelResponse("CANCELED", "DONE", 1000L);

		given(paymentTransactionService.prepareCancel(10L, 1L, request))
			.willReturn(PaymentCancelPreparation.required(command));
		given(tossPaymentsClient.cancel(command)).willReturn(tossResponse);
		given(paymentTransactionService.completeCancel(
			command,
			"transaction-key",
			LocalDateTime.of(2026, 6, 16, 11, 0)
		)).willReturn(expected);

		PaymentCancelResponse response = paymentService.cancel(10L, 1L, request);

		assertThat(response).isEqualTo(expected);
	}

	@Test
	@DisplayName("토스 취소 성공 후 DB 완료 처리 실패 시 환불 실패 상태로 기록한다")
	void recordFailedCancelWhenCompleteFails() throws Exception {
		PaymentCancelRequest request = new PaymentCancelRequest("구매자 변심", "cancel-key");
		PaymentCancelCommand command = cancelCommand();
		JsonNode tossResponse = cancelResponse("CANCELED", "DONE", 1000L);

		given(paymentTransactionService.prepareCancel(10L, 1L, request))
			.willReturn(PaymentCancelPreparation.required(command));
		given(tossPaymentsClient.cancel(command)).willReturn(tossResponse);
		given(paymentTransactionService.completeCancel(
			command,
			"transaction-key",
			LocalDateTime.of(2026, 6, 16, 11, 0)
		)).willThrow(new TransactionSystemException("commit failed"));

		assertThatThrownBy(() -> paymentService.cancel(10L, 1L, request))
			.isInstanceOf(TransactionSystemException.class);

		verify(paymentTransactionService).failCancel("cancel-key");
	}

	@Test
	@DisplayName("실패한 일반 환불을 스케줄러에서 재시도한다")
	void retryFailedCancels() throws Exception {
		PaymentCancelCommand command = cancelCommand();
		JsonNode tossResponse = cancelResponse("CANCELED", "DONE", 1000L);

		given(paymentTransactionService.prepareFailedCancels()).willReturn(List.of(command));
		given(tossPaymentsClient.cancel(command)).willReturn(tossResponse);

		paymentService.retryFailedCancels();

		verify(paymentTransactionService).completeCancel(
			command,
			"transaction-key",
			LocalDateTime.of(2026, 6, 16, 11, 0)
		);
	}

	@Test
	@DisplayName("이미 취소된 결제는 토스 API를 다시 호출하지 않는다")
	void returnExistingCanceledPayment() {
		PaymentCancelRequest request = new PaymentCancelRequest("구매자 변심", "cancel-key");
		PaymentCancelResponse expected = canceledResponse();
		given(paymentTransactionService.prepareCancel(10L, 1L, request))
			.willReturn(PaymentCancelPreparation.completed(expected));

		PaymentCancelResponse response = paymentService.cancel(10L, 1L, request);

		assertThat(response).isEqualTo(expected);
		verify(tossPaymentsClient, never()).cancel(any());
	}

	@Test
	@DisplayName("토스 취소 호출이 실패하면 환불 요청을 실패 처리한다")
	void failCancelWhenTossCallFails() {
		PaymentCancelRequest request = new PaymentCancelRequest("구매자 변심", "cancel-key");
		PaymentCancelCommand command = cancelCommand();
		given(paymentTransactionService.prepareCancel(10L, 1L, request))
			.willReturn(PaymentCancelPreparation.required(command));
		given(tossPaymentsClient.cancel(command)).willThrow(new RestClientException("toss error"));

		assertThatThrownBy(() -> paymentService.cancel(10L, 1L, request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PAYMENT_CANCEL_FAILED);

		verify(paymentTransactionService).failCancel("cancel-key");
		verify(paymentTransactionService, never()).completeCancel(any(), any(), any());
	}

	@Test
	@DisplayName("토스 취소 응답이 전액 취소 완료가 아니면 완료 처리하지 않는다")
	void rejectPartialCancelResponse() throws Exception {
		PaymentCancelRequest request = new PaymentCancelRequest("구매자 변심", "cancel-key");
		PaymentCancelCommand command = cancelCommand();
		given(paymentTransactionService.prepareCancel(10L, 1L, request))
			.willReturn(PaymentCancelPreparation.required(command));
		given(tossPaymentsClient.cancel(command)).willReturn(cancelResponse("PARTIAL_CANCELED", "DONE", 500L));

		assertThatThrownBy(() -> paymentService.cancel(10L, 1L, request))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PAYMENT_CANCEL_RESPONSE_MISMATCH);

		verify(paymentTransactionService).failCancel("cancel-key");
		verify(paymentTransactionService, never()).completeCancel(any(), any(), any());
	}

	private PaymentConfirmResponse paidResponse() {
		return new PaymentConfirmResponse(
			10L,
			PaymentType.POPUP,
			"order-id",
			"payment-key",
			"예약 결제",
			1000L,
			PaymentStatus.PAID,
			APPROVED_AT
		);
	}

	private JsonNode tossResponse(String paymentKey, String orderId, long amount) throws Exception {
		return JsonMapper.builder().build()
			.readTree("""
				{
				  "status": "DONE",
				  "paymentKey": "%s",
				  "orderId": "%s",
				  "totalAmount": %d,
				  "approvedAt": "2026-06-16T10:00:00+09:00"
				}
					""".formatted(paymentKey, orderId, amount));
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

	private PaymentCancelResponse canceledResponse() {
		return new PaymentCancelResponse(
			20L,
			10L,
			"payment-key",
			"order-id",
			1000L,
			PaymentStatus.CANCELED,
			PaymentRefundStatus.DONE,
			"transaction-key",
			LocalDateTime.of(2026, 6, 16, 11, 0)
		);
	}

	private JsonNode cancelResponse(String status, String cancelStatus, long cancelAmount) throws Exception {
		return JsonMapper.builder().build()
			.readTree("""
				{
				  "status": "%s",
				  "paymentKey": "payment-key",
				  "orderId": "order-id",
				  "balanceAmount": 0,
				  "cancels": [{
				    "cancelStatus": "%s",
				    "cancelAmount": %d,
				    "transactionKey": "transaction-key",
				    "canceledAt": "2026-06-16T11:00:00+09:00"
				  }]
				}
				""".formatted(status, cancelStatus, cancelAmount));
	}
}
