package com.back.popspot.domain.goods.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderItem;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;

import lombok.Getter;

@Getter
public class GoodsOrderSummaryResponse {

	private final Long goodsOrderId;
	private final List<SummaryItem> items;
	private final int finalAmount;
	private final GoodsOrderStatus status;
	private final LocalDateTime orderedAt;

	private GoodsOrderSummaryResponse(Long goodsOrderId, List<SummaryItem> items,
			int finalAmount, GoodsOrderStatus status, LocalDateTime orderedAt) {
		this.goodsOrderId = goodsOrderId;
		this.items = items;
		this.finalAmount = finalAmount;
		this.status = status;
		this.orderedAt = orderedAt;
	}

	public static GoodsOrderSummaryResponse from(GoodsOrder order, List<GoodsOrderItem> items) {
		List<SummaryItem> summaryItems = items.stream()
				.map(SummaryItem::from)
				.collect(Collectors.toList());
		return new GoodsOrderSummaryResponse(
				order.getId(),
				summaryItems,
				order.getFinalAmount(),
				order.getStatus(),
				order.getCreatedAt()
		);
	}

	@Getter
	public static class SummaryItem {

		private final Long goodsId;
		private final String goodsName;
		private final int quantity;
		private final int itemAmount;

		private SummaryItem(Long goodsId, String goodsName, int quantity, int itemAmount) {
			this.goodsId = goodsId;
			this.goodsName = goodsName;
			this.quantity = quantity;
			this.itemAmount = itemAmount;
		}

		public static SummaryItem from(GoodsOrderItem item) {
			return new SummaryItem(
					item.getGoods().getId(),
					item.getGoods().getName(),
					item.getQuantity(),
					item.getItemAmount()
			);
		}
	}
}
