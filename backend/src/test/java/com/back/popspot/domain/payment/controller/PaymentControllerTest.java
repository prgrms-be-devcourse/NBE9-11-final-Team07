package com.back.popspot.domain.payment.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.domain.payment.dto.PaymentConfirmResponse;
import com.back.popspot.domain.payment.dto.PaymentCancelRequest;
import com.back.popspot.domain.payment.dto.PaymentCancelResponse;
import com.back.popspot.domain.payment.entity.PaymentRefundStatus;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.service.PaymentService;
import com.back.popspot.global.exception.GlobalExceptionHandler;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {
	@Mock
	private PaymentService paymentService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.setCustomArgumentResolvers(new AuthenticationPrincipalResolver())
			.build();
	}

	@Test
	@DisplayName("결제 승인 요청을 서비스에 전달한다")
	void confirmPayment() throws Exception {
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 1000L);
		PaymentConfirmResponse response = new PaymentConfirmResponse(
			10L,
			PaymentType.POPUP,
			"order-id",
			"payment-key",
			"예약 결제",
			1000L,
			PaymentStatus.PAID,
			LocalDateTime.of(2026, 6, 16, 10, 0)
		);
		given(paymentService.confirm(request)).willReturn(response);

		mockMvc.perform(post("/api/payments/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "paymentKey": "payment-key",
					  "orderId": "order-id",
					  "amount": 1000
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentId").value(10))
			.andExpect(jsonPath("$.paymentType").value("POPUP"))
			.andExpect(jsonPath("$.orderId").value("order-id"))
			.andExpect(jsonPath("$.paymentKey").value("payment-key"))
			.andExpect(jsonPath("$.status").value("PAID"));

		verify(paymentService).confirm(request);
	}

	@Test
	@DisplayName("결제 승인 요청값이 유효하지 않으면 400을 반환한다")
	void rejectInvalidConfirmRequest() throws Exception {
		mockMvc.perform(post("/api/payments/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "paymentKey": "",
					  "orderId": "",
					  "amount": 0
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

		verifyNoInteractions(paymentService);
	}

	@Test
	@DisplayName("결제 전액 취소 요청을 서비스에 전달한다")
	void cancelPayment() throws Exception {
		PaymentCancelRequest request = new PaymentCancelRequest("구매자 변심", "cancel-key");
		PaymentCancelResponse response = new PaymentCancelResponse(
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
		given(paymentService.cancel(10L, 1L, request)).willReturn(response);

		mockMvc.perform(post("/api/payments/10/cancel")
				.principal(authentication(1L))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "cancelReason": "구매자 변심",
					  "idempotencyKey": "cancel-key"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentId").value(10))
			.andExpect(jsonPath("$.paymentStatus").value("CANCELED"))
			.andExpect(jsonPath("$.refundStatus").value("DONE"))
			.andExpect(jsonPath("$.transactionKey").value("transaction-key"));

		verify(paymentService).cancel(10L, 1L, request);
	}

	@Test
	@DisplayName("결제 취소 사유와 멱등키가 유효하지 않으면 400을 반환한다")
	void rejectInvalidCancelRequest() throws Exception {
		mockMvc.perform(post("/api/payments/10/cancel")
				.principal(authentication(1L))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "cancelReason": "",
					  "idempotencyKey": ""
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
	}

	private UsernamePasswordAuthenticationToken authentication(Long userId) {
		return new UsernamePasswordAuthenticationToken(userId, null, List.of());
	}

	private static class AuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {
		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
		}

		@Override
		public Object resolveArgument(
			MethodParameter parameter,
			ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest,
			WebDataBinderFactory binderFactory
		) {
			Authentication authentication = (Authentication)webRequest.getUserPrincipal();
			return authentication.getPrincipal();
		}
	}
}
