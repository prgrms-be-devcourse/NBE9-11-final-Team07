package com.back.popspot.domain.goods.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.goods.dto.GoodsOrderCreateRequest;
import com.back.popspot.domain.goods.dto.GoodsOrderCreateResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderDetailResponse.DetailItem;
import com.back.popspot.domain.goods.dto.GoodsOrderRefundResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderSummaryResponse;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderItem;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.repository.GoodsImageRepository;
import com.back.popspot.domain.goods.repository.GoodsOrderItemRepository;
import com.back.popspot.domain.goods.repository.GoodsOrderRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoodsOrderService {

	private final UserRepository userRepository;
	private final GoodsRepository goodsRepository;
	private final GoodsImageRepository goodsImageRepository;
	private final GoodsOrderRepository goodsOrderRepository;
	private final GoodsOrderItemRepository goodsOrderItemRepository;
	private final PaymentRepository paymentRepository;

	@Transactional
	public GoodsOrderCreateResponse createOrder(Long userId, GoodsOrderCreateRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		Payment existingIdempotentPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey())
				.orElse(null);
		if (existingIdempotentPayment != null) {
			if (existingIdempotentPayment.getGoodsOrder() == null
					|| !existingIdempotentPayment.getGoodsOrder().getUser().getId().equals(userId)
					|| existingIdempotentPayment.getPaymentType() != PaymentType.GOODS) {
				throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
			}

			GoodsOrder existingOrder = existingIdempotentPayment.getGoodsOrder();
			List<GoodsOrderItem> existingItems = goodsOrderItemRepository.findByGoodsOrder_Id(existingOrder.getId());
			return GoodsOrderCreateResponse.from(existingOrder, existingItems, existingIdempotentPayment);
		}

		Map<Long, Integer> quantityByGoodsId = request.getItems().stream()
				.collect(Collectors.groupingBy(
						GoodsOrderCreateRequest.OrderItemRequest::getGoodsId,
						Collectors.summingInt(GoodsOrderCreateRequest.OrderItemRequest::getQuantity)
				));

		Map<Long, Goods> goodsMap = new HashMap<>();
		int totalAmount = 0;
		for (Map.Entry<Long, Integer> entry : quantityByGoodsId.entrySet()) {
			Goods goods = goodsRepository.findByIdAndDeletedAtIsNull(entry.getKey())
					.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
			if (goods.getStatus() != GoodsStatus.ON_SALE) {
				throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
			}
			if (goods.getStock() < entry.getValue()) {
				throw new BusinessException(ErrorCode.GOODS_OUT_OF_STOCK);
			}
			goods.decreaseStock(entry.getValue());
			totalAmount += goods.getPrice() * entry.getValue();
			goodsMap.put(entry.getKey(), goods);
		}

		int discountAmount = 0;
		int finalAmount = totalAmount - discountAmount;

		GoodsOrder order = goodsOrderRepository.save(new GoodsOrder(
				user, totalAmount, discountAmount, finalAmount,
				GoodsOrderStatus.PENDING,
				request.getReceiverName(), request.getReceiverPhone(),
				request.getPostalCode(), request.getAddress(), request.getAddressDetail()
		));

		List<GoodsOrderItem> savedItems = request.getItems().stream()
				.map(itemReq -> {
					Goods goods = goodsMap.get(itemReq.getGoodsId());
					int itemAmount = goods.getPrice() * itemReq.getQuantity();
					return goodsOrderItemRepository.save(
							new GoodsOrderItem(order, goods, itemReq.getQuantity(), goods.getPrice(), itemAmount)
					);
				})
				.collect(Collectors.toList());

		Payment payment = paymentRepository.save(Payment.createReadyGoodsOrderPayment(
				user,
				order,
				UUID.randomUUID().toString(),
				createOrderName(savedItems),
				order.getFinalAmount(),
				request.getIdempotencyKey()
		));

		return GoodsOrderCreateResponse.from(order, savedItems, payment);
	}

	private String createOrderName(List<GoodsOrderItem> items) {
		if (items.size() == 1) {
			return items.get(0).getGoods().getName();
		}

		return items.get(0).getGoods().getName() + " 외 " + (items.size() - 1) + "건";
	}

	@Transactional
	public GoodsOrderRefundResponse refundOrder(Long userId, Long goodsOrderId) {
		GoodsOrder order = goodsOrderRepository.findById(goodsOrderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (!order.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (order.getStatus() != GoodsOrderStatus.PAID) {
			throw new BusinessException(ErrorCode.GOODS_ORDER_REFUND_NOT_ALLOWED);
		}
		order.updateStatus(GoodsOrderStatus.REFUNDED);
		return GoodsOrderRefundResponse.from(order);
	}

	@Transactional(readOnly = true)
	public PageResponse<GoodsOrderSummaryResponse> getMyOrders(Long userId, GoodsOrderStatus status, Pageable pageable) {
		Page<GoodsOrder> orderPage = (status != null)
				? goodsOrderRepository.findByUser_IdAndStatus(userId, status, pageable)
				: goodsOrderRepository.findByUser_Id(userId, pageable);

		List<Long> orderIds = orderPage.getContent().stream()
				.map(GoodsOrder::getId)
				.collect(Collectors.toList());

		Map<Long, List<GoodsOrderItem>> itemsByOrderId = goodsOrderItemRepository
				.findByGoodsOrder_IdIn(orderIds)
				.stream()
				.collect(Collectors.groupingBy(item -> item.getGoodsOrder().getId()));

		Page<GoodsOrderSummaryResponse> responsePage = orderPage.map(order ->
				GoodsOrderSummaryResponse.from(order, itemsByOrderId.getOrDefault(order.getId(), List.of()))
		);

		return PageResponse.from(responsePage);
	}

	@Transactional(readOnly = true)
	public GoodsOrderDetailResponse getOrderDetail(Long userId, Long goodsOrderId) {
		GoodsOrder order = goodsOrderRepository.findById(goodsOrderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (!order.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		List<GoodsOrderItem> items = goodsOrderItemRepository.findByGoodsOrder_Id(goodsOrderId);
		List<DetailItem> detailItems = items.stream()
				.map(item -> {
					String thumbnailImageKey = goodsImageRepository
							.findFirstByGoods_IdAndImageTypeOrderByIdAsc(item.getGoods().getId(), GoodsImageType.PRODUCT)
							.map(GoodsImage::getImageKey)
							.orElse(null);
					return DetailItem.from(item, thumbnailImageKey);
				})
				.collect(Collectors.toList());

		return GoodsOrderDetailResponse.from(order, detailItems);
	}
}
