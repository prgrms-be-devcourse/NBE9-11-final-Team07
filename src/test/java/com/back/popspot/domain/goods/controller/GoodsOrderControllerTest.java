package com.back.popspot.domain.goods.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.back.popspot.domain.goods.dto.GoodsOrderCreateResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderRefundResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderSummaryResponse;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderItem;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.service.GoodsOrderService;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.dto.PageResponse;

import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;

@WebMvcTest(GoodsOrderController.class)
class GoodsOrderControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GoodsOrderService goodsOrderService;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private User user;
	private Goods goods;
	private GoodsOrder pendingOrder;

	@BeforeEach
	void setUp() {
		user = new User();
		ReflectionTestUtils.setField(user, "id", 1L);

		goods = new Goods();
		ReflectionTestUtils.setField(goods, "id", 10L);
		ReflectionTestUtils.setField(goods, "name", "테스트굿즈");
		ReflectionTestUtils.setField(goods, "price", 5000);
		ReflectionTestUtils.setField(goods, "stock", 10);
		ReflectionTestUtils.setField(goods, "status", GoodsStatus.ON_SALE);

		pendingOrder = new GoodsOrder(user, 10000, 0, 10000, GoodsOrderStatus.PENDING,
				"홍길동", "010-1234-5678", "12345", "서울시", null);
		ReflectionTestUtils.setField(pendingOrder, "id", 100L);
	}

	private UsernamePasswordAuthenticationToken auth() {
		return new UsernamePasswordAuthenticationToken(1L, null, List.of());
	}

	@TestConfiguration
	static class SecurityConfig {
		@Bean
		SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
			http.csrf(csrf -> csrf.disable())
					.authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
			return http.build();
		}
	}

	@Test
	void createOrder_성공_201() throws Exception {
		GoodsOrderItem item = new GoodsOrderItem(pendingOrder, goods, 2, 5000, 10000);
		GoodsOrderCreateResponse response = GoodsOrderCreateResponse.from(pendingOrder, List.of(item));
		given(goodsOrderService.createOrder(eq(1L), any())).willReturn(response);

		String requestBody = """
				{
				  "items": [{"goodsId": 10, "quantity": 2}],
				  "receiverName": "홍길동",
				  "receiverPhone": "010-1234-5678",
				  "postalCode": "12345",
				  "address": "서울시"
				}
				""";

		mockMvc.perform(post("/api/v1/goods-orders")
						.with(authentication(auth()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.goodsOrderId").value(100));
	}

	@Test
	void refundOrder_성공_200() throws Exception {
		GoodsOrder refundedOrder = new GoodsOrder(user, 10000, 0, 10000, GoodsOrderStatus.REFUNDED,
				"홍길동", "010-1234-5678", "12345", "서울시", null);
		ReflectionTestUtils.setField(refundedOrder, "id", 100L);
		GoodsOrderRefundResponse response = GoodsOrderRefundResponse.from(refundedOrder);
		given(goodsOrderService.refundOrder(eq(1L), eq(100L))).willReturn(response);

		mockMvc.perform(post("/api/v1/goods-orders/100/refund")
						.with(authentication(auth()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.status").value("REFUNDED"));
	}

	@Test
	void getMyOrders_성공_200() throws Exception {
		GoodsOrderSummaryResponse summary = GoodsOrderSummaryResponse.from(pendingOrder, List.of());
		PageResponse<GoodsOrderSummaryResponse> response = PageResponse.from(new PageImpl<>(List.of(summary)));
		given(goodsOrderService.getMyOrders(eq(1L), any(), any())).willReturn(response);

		mockMvc.perform(get("/api/v1/me/goods-orders")
						.with(authentication(auth())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	void getOrderDetail_성공_200() throws Exception {
		GoodsOrderDetailResponse response = GoodsOrderDetailResponse.from(pendingOrder, List.of());
		given(goodsOrderService.getOrderDetail(eq(1L), eq(100L))).willReturn(response);

		mockMvc.perform(get("/api/v1/goods-orders/100")
						.with(authentication(auth())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data.goodsOrderId").value(100));
	}
}
