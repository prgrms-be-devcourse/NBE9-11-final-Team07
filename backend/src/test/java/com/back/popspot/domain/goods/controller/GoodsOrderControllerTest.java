package com.back.popspot.domain.goods.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import com.back.popspot.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("굿즈 주문 API")
class GoodsOrderControllerTest extends IntegrationTestSupport {

	@MockitoBean
	private GoodsOrderService goodsOrderService;

	private User user;
	private Goods goods;
	private GoodsOrder pendingOrder;

	@BeforeEach
	void setUp() {
		user = User.create("test@test.com", "테스터");
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

	@Test
	@DisplayName("굿즈 주문 생성 시 201과 주문 정보를 반환한다")
	void createOrder_성공_201() throws Exception {
		GoodsOrderItem item = new GoodsOrderItem(pendingOrder, goods, 2, 5000, 10000);
		Payment payment = Payment.createReadyGoodsOrderPayment(
				user,
				pendingOrder,
				"order-id",
				"테스트굿즈",
				10000L,
				"idempotency-key"
		);
		GoodsOrderCreateResponse response = GoodsOrderCreateResponse.from(pendingOrder, List.of(item), payment);
		given(goodsOrderService.createOrder(eq(1L), any())).willReturn(response);

		String requestBody = """
				{
				  "items": [{"goodsId": 10, "quantity": 2}],
				  "idempotencyKey": "idempotency-key",
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
				.andExpect(jsonPath("$.data.goodsOrderId").value(100))
				.andExpect(jsonPath("$.data.orderId").value("order-id"))
				.andExpect(jsonPath("$.data.orderName").value("테스트굿즈"))
				.andExpect(jsonPath("$.data.amount").value(10000));
	}

	@Test
	@DisplayName("굿즈 주문 환불 시 200과 REFUNDED 상태를 반환한다")
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
	@DisplayName("내 주문 목록 조회 시 200과 페이지 응답을 반환한다")
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
	@DisplayName("굿즈 주문 상세 조회 시 200과 주문 상세를 반환한다")
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
