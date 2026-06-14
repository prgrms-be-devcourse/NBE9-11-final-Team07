package com.back.popspot.domain.goods.service;

import java.util.List;
import java.util.Map;
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

	@Transactional
	public GoodsOrderCreateResponse createOrder(Long userId, GoodsOrderCreateRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		int totalAmount = 0;
		for (GoodsOrderCreateRequest.OrderItemRequest itemReq : request.getItems()) {
			Goods goods = goodsRepository.findByIdAndDeletedAtIsNull(itemReq.getGoodsId())
					.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
			if (goods.getStatus() != GoodsStatus.ON_SALE) {
				throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
			}
			if (goods.getStock() < itemReq.getQuantity()) {
				throw new BusinessException(ErrorCode.GOODS_OUT_OF_STOCK);
			}
			totalAmount += goods.getPrice() * itemReq.getQuantity();
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
					Goods goods = goodsRepository.findByIdAndDeletedAtIsNull(itemReq.getGoodsId())
							.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
					goods.decreaseStock(itemReq.getQuantity());
					int itemAmount = goods.getPrice() * itemReq.getQuantity();
					return goodsOrderItemRepository.save(
							new GoodsOrderItem(order, goods, itemReq.getQuantity(), goods.getPrice(), itemAmount)
					);
				})
				.collect(Collectors.toList());

		return GoodsOrderCreateResponse.from(order, savedItems);
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
