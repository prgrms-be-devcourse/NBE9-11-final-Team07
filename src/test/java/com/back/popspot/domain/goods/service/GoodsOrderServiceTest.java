package com.back.popspot.domain.goods.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.goods.dto.GoodsOrderCreateRequest;
import com.back.popspot.domain.goods.dto.GoodsOrderCreateResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderRefundResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderSummaryResponse;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderItem;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.repository.GoodsImageRepository;
import com.back.popspot.domain.goods.repository.GoodsOrderItemRepository;
import com.back.popspot.domain.goods.repository.GoodsOrderRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class GoodsOrderServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private GoodsRepository goodsRepository;

	@Mock
	private GoodsImageRepository goodsImageRepository;

	@Mock
	private GoodsOrderRepository goodsOrderRepository;

	@Mock
	private GoodsOrderItemRepository goodsOrderItemRepository;

	@InjectMocks
	private GoodsOrderService goodsOrderService;

	private User user;
	private Goods goods;
	private GoodsOrder order;
	private GoodsOrderItem orderItem;

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
		ReflectionTestUtils.setField(goods, "deletedAt", null);

		order = new GoodsOrder(user, 10000, 0, 10000, GoodsOrderStatus.PENDING,
				"홍길동", "010-1234-5678", "12345", "서울시", null);
		ReflectionTestUtils.setField(order, "id", 100L);

		orderItem = new GoodsOrderItem(order, goods, 2, 5000, 10000);
		ReflectionTestUtils.setField(orderItem, "id", 1L);
	}

	private GoodsOrderCreateRequest buildCreateRequest(Long goodsId, int quantity) {
		GoodsOrderCreateRequest.OrderItemRequest itemReq = new GoodsOrderCreateRequest.OrderItemRequest();
		ReflectionTestUtils.setField(itemReq, "goodsId", goodsId);
		ReflectionTestUtils.setField(itemReq, "quantity", quantity);

		GoodsOrderCreateRequest request = new GoodsOrderCreateRequest();
		ReflectionTestUtils.setField(request, "items", List.of(itemReq));
		ReflectionTestUtils.setField(request, "couponId", null);
		ReflectionTestUtils.setField(request, "receiverName", "홍길동");
		ReflectionTestUtils.setField(request, "receiverPhone", "010-1234-5678");
		ReflectionTestUtils.setField(request, "postalCode", "12345");
		ReflectionTestUtils.setField(request, "address", "서울시");
		ReflectionTestUtils.setField(request, "addressDetail", null);
		return request;
	}

	@Test
	void createOrder_성공() {
		given(userRepository.findById(1L)).willReturn(Optional.of(user));
		given(goodsRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(goods));
		given(goodsOrderRepository.save(any())).willReturn(order);
		given(goodsOrderItemRepository.save(any())).willReturn(orderItem);

		GoodsOrderCreateResponse response = goodsOrderService.createOrder(1L, buildCreateRequest(10L, 2));

		assertThat(response.getGoodsOrderId()).isEqualTo(100L);
		assertThat(response.getStatus()).isEqualTo(GoodsOrderStatus.PENDING);
	}

	@Test
	void createOrder_존재하지_않는_굿즈_RESOURCE_NOT_FOUND() {
		given(userRepository.findById(1L)).willReturn(Optional.of(user));
		given(goodsRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> goodsOrderService.createOrder(1L, buildCreateRequest(999L, 1)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
	}

	@Test
	void createOrder_판매중이_아닌_굿즈_GOODS_NOT_ON_SALE() {
		ReflectionTestUtils.setField(goods, "status", GoodsStatus.ENDED);
		given(userRepository.findById(1L)).willReturn(Optional.of(user));
		given(goodsRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(goods));

		assertThatThrownBy(() -> goodsOrderService.createOrder(1L, buildCreateRequest(10L, 1)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.GOODS_NOT_ON_SALE);
	}

	@Test
	void createOrder_재고부족_GOODS_OUT_OF_STOCK() {
		given(userRepository.findById(1L)).willReturn(Optional.of(user));
		given(goodsRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(goods));

		assertThatThrownBy(() -> goodsOrderService.createOrder(1L, buildCreateRequest(10L, 100)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.GOODS_OUT_OF_STOCK);
	}

	@Test
	void refundOrder_성공() {
		GoodsOrder paidOrder = new GoodsOrder(user, 10000, 0, 10000, GoodsOrderStatus.PAID,
				"홍길동", "010-1234-5678", "12345", "서울시", null);
		ReflectionTestUtils.setField(paidOrder, "id", 100L);
		given(goodsOrderRepository.findById(100L)).willReturn(Optional.of(paidOrder));

		GoodsOrderRefundResponse response = goodsOrderService.refundOrder(1L, 100L);

		assertThat(response.getStatus()).isEqualTo(GoodsOrderStatus.REFUNDED);
	}

	@Test
	void refundOrder_주문_없음_RESOURCE_NOT_FOUND() {
		given(goodsOrderRepository.findById(999L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> goodsOrderService.refundOrder(1L, 999L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
	}

	@Test
	void refundOrder_본인_아님_FORBIDDEN() {
		given(goodsOrderRepository.findById(100L)).willReturn(Optional.of(order));

		assertThatThrownBy(() -> goodsOrderService.refundOrder(99L, 100L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	void refundOrder_PAID_아님_GOODS_ORDER_REFUND_NOT_ALLOWED() {
		given(goodsOrderRepository.findById(100L)).willReturn(Optional.of(order));

		assertThatThrownBy(() -> goodsOrderService.refundOrder(1L, 100L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.GOODS_ORDER_REFUND_NOT_ALLOWED);
	}

	@Test
	void getMyOrders_성공_상태필터없음() {
		Page<GoodsOrder> page = new PageImpl<>(List.of(order));
		given(goodsOrderRepository.findByUser_Id(1L, PageRequest.of(0, 20))).willReturn(page);
		given(goodsOrderItemRepository.findByGoodsOrder_IdIn(List.of(100L))).willReturn(List.of(orderItem));

		PageResponse<GoodsOrderSummaryResponse> response =
				goodsOrderService.getMyOrders(1L, null, PageRequest.of(0, 20));

		assertThat(response.getContent()).hasSize(1);
		assertThat(response.getContent().get(0).getGoodsOrderId()).isEqualTo(100L);
	}

	@Test
	void getMyOrders_성공_상태필터있음() {
		Page<GoodsOrder> page = new PageImpl<>(List.of(order));
		given(goodsOrderRepository.findByUser_IdAndStatus(1L, GoodsOrderStatus.PENDING, PageRequest.of(0, 20)))
				.willReturn(page);
		given(goodsOrderItemRepository.findByGoodsOrder_IdIn(List.of(100L))).willReturn(List.of(orderItem));

		PageResponse<GoodsOrderSummaryResponse> response =
				goodsOrderService.getMyOrders(1L, GoodsOrderStatus.PENDING, PageRequest.of(0, 20));

		assertThat(response.getContent()).hasSize(1);
	}

	@Test
	void getOrderDetail_성공() {
		given(goodsOrderRepository.findById(100L)).willReturn(Optional.of(order));
		given(goodsOrderItemRepository.findByGoodsOrder_Id(100L)).willReturn(List.of(orderItem));
		given(goodsImageRepository.findFirstByGoods_IdAndImageTypeOrderByIdAsc(any(), any()))
				.willReturn(Optional.empty());

		GoodsOrderDetailResponse response = goodsOrderService.getOrderDetail(1L, 100L);

		assertThat(response.getGoodsOrderId()).isEqualTo(100L);
		assertThat(response.getItems()).hasSize(1);
		assertThat(response.getItems().get(0).getThumbnailImageKey()).isNull();
	}

	@Test
	void getOrderDetail_본인_아님_FORBIDDEN() {
		given(goodsOrderRepository.findById(100L)).willReturn(Optional.of(order));

		assertThatThrownBy(() -> goodsOrderService.getOrderDetail(99L, 100L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode")
				.isEqualTo(ErrorCode.FORBIDDEN);
	}
}
