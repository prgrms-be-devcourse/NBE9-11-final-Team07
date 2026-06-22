package com.back.popspot.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.back.popspot.domain.payment.client.TossPaymentsClient;
import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
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
}
