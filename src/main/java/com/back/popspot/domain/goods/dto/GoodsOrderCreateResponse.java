package com.back.popspot.domain.goods.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderItem;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;

import lombok.Getter;

@Getter
public class GoodsOrderCreateResponse {

	private final Long goodsOrderId;
	private final List<OrderItemResult> items;
	private final int totalAmount;
	private final Integer discountAmount;
	private final int finalAmount;
	private final GoodsOrderStatus status;

	private GoodsOrderCreateResponse(Long goodsOrderId, List<OrderItemResult> items,
			int totalAmount, Integer discountAmount, int finalAmount, GoodsOrderStatus status) {
		this.goodsOrderId = goodsOrderId;
		this.items = items;
		this.totalAmount = totalAmount;
		this.discountAmount = discountAmount;
		this.finalAmount = finalAmount;
		this.status = status;
	}

	public static GoodsOrderCreateResponse from(GoodsOrder order, List<GoodsOrderItem> items) {
		List<OrderItemResult> itemResults = items.stream()
				.map(OrderItemResult::from)
				.collect(Collectors.toList());
		return new GoodsOrderCreateResponse(
				order.getId(),
				itemResults,
				order.getTotalAmount(),
				order.getDiscountAmount(),
				order.getFinalAmount(),
				order.getStatus()
		);
	}

	@Getter
	public static class OrderItemResult {

		private final Long goodsId;
		private final String goodsName;
		private final int quantity;
		private final int unitPrice;
		private final int itemAmount;

		private OrderItemResult(Long goodsId, String goodsName, int quantity, int unitPrice, int itemAmount) {
			this.goodsId = goodsId;
			this.goodsName = goodsName;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
			this.itemAmount = itemAmount;
		}

		public static OrderItemResult from(GoodsOrderItem item) {
			return new OrderItemResult(
					item.getGoods().getId(),
					item.getGoods().getName(),
					item.getQuantity(),
					item.getUnitPrice(),
					item.getItemAmount()
			);
		}
	}
}
