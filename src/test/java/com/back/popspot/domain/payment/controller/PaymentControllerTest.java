package com.back.popspot.domain.payment.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.back.popspot.domain.payment.client.TossPaymentsClient;
import com.back.popspot.domain.payment.dto.PaymentConfirmRequest;
import com.back.popspot.global.exception.GlobalExceptionHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {
	@Mock
	private TossPaymentsClient tossPaymentsClient;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(tossPaymentsClient))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("결제 승인 요청을 토스 클라이언트에 전달한다")
	void confirmPayment() throws Exception {
		PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key", "order-id", 1000L);
		JsonNode response = JsonMapper.builder().build().readTree("{\"status\":\"DONE\"}");
		given(tossPaymentsClient.confirm(request)).willReturn(response);

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
			.andExpect(jsonPath("$.status").value("DONE"));

		verify(tossPaymentsClient).confirm(request);
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

		verifyNoInteractions(tossPaymentsClient);
	}
}
