package com.back.popspot.domain.payment.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.back.popspot.domain.payment.dto.DevPaymentCreateRequest;
import com.back.popspot.domain.payment.dto.DevPaymentCreateResponse;
import com.back.popspot.domain.payment.entity.PaymentStatus;
import com.back.popspot.domain.payment.service.DevPaymentService;
import com.back.popspot.global.exception.GlobalExceptionHandler;

@ExtendWith(MockitoExtension.class)
class DevPaymentControllerTest {
	@Mock
	private DevPaymentService devPaymentService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new DevPaymentController(devPaymentService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.setCustomArgumentResolvers(new AuthenticationPrincipalResolver())
			.build();
	}

	@Test
	@DisplayName("개발용 결제 주문 생성 시 201을 반환한다")
	void createDevPayment() throws Exception {
		DevPaymentCreateRequest request = new DevPaymentCreateRequest("테스트 상품", 1000L);
		DevPaymentCreateResponse response = new DevPaymentCreateResponse(
			10L,
			"dev-order-id",
			request.orderName(),
			request.amount(),
			PaymentStatus.READY
		);
		given(devPaymentService.create(1L, request)).willReturn(response);

		mockMvc.perform(post("/api/dev/payments")
				.principal(authentication(1L))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "orderName": "테스트 상품",
					  "amount": 1000
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.data.paymentId").value(10))
			.andExpect(jsonPath("$.data.orderId").value("dev-order-id"))
			.andExpect(jsonPath("$.data.orderName").value("테스트 상품"))
			.andExpect(jsonPath("$.data.amount").value(1000))
			.andExpect(jsonPath("$.data.status").value("READY"));
	}

	@Test
	@DisplayName("개발용 결제 금액이 1원 미만이면 400을 반환한다")
	void rejectInvalidAmount() throws Exception {
		mockMvc.perform(post("/api/dev/payments")
				.principal(authentication(1L))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "orderName": "테스트 상품",
					  "amount": 0
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
