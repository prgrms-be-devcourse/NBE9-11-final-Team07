package com.back.popspot.domain.goods.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderItem;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;

import lombok.Getter;

@Getter
public class GoodsOrderDetailResponse {

	private final Long goodsOrderId;
	private final List<DetailItem> items;
	private final int totalAmount;
	private final Integer discountAmount;
	private final int finalAmount;
	private final GoodsOrderStatus status;
	private final String receiverName;
	private final String receiverPhone;
	private final String postalCode;
	private final String address;
	private final String addressDetail;
	private final LocalDateTime orderedAt;

	private GoodsOrderDetailResponse(Long goodsOrderId, List<DetailItem> items,
			int totalAmount, Integer discountAmount, int finalAmount, GoodsOrderStatus status,
			String receiverName, String receiverPhone, String postalCode,
			String address, String addressDetail, LocalDateTime orderedAt) {
		this.goodsOrderId = goodsOrderId;
		this.items = items;
		this.totalAmount = totalAmount;
		this.discountAmount = discountAmount;
		this.finalAmount = finalAmount;
		this.status = status;
		this.receiverName = receiverName;
		this.receiverPhone = receiverPhone;
		this.postalCode = postalCode;
		this.address = address;
		this.addressDetail = addressDetail;
		this.orderedAt = orderedAt;
	}

	public static GoodsOrderDetailResponse from(GoodsOrder order, List<DetailItem> items) {
		return new GoodsOrderDetailResponse(
				order.getId(),
				items,
				order.getTotalAmount(),
				order.getDiscountAmount(),
				order.getFinalAmount(),
				order.getStatus(),
				order.getReceiverName(),
				order.getReceiverPhone(),
				order.getPostalCode(),
				order.getAddress(),
				order.getAddressDetail(),
				order.getCreatedAt()
		);
	}

	@Getter
	public static class DetailItem {

		private final Long goodsId;
		private final String goodsName;
		private final String thumbnailImageKey;
		private final int quantity;
		private final int unitPrice;
		private final int itemAmount;

		private DetailItem(Long goodsId, String goodsName, String thumbnailImageKey,
				int quantity, int unitPrice, int itemAmount) {
			this.goodsId = goodsId;
			this.goodsName = goodsName;
			this.thumbnailImageKey = thumbnailImageKey;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
			this.itemAmount = itemAmount;
		}

		public static DetailItem from(GoodsOrderItem item, String thumbnailImageKey) {
			return new DetailItem(
					item.getGoods().getId(),
					item.getGoods().getName(),
					thumbnailImageKey,
					item.getQuantity(),
					item.getUnitPrice(),
					item.getItemAmount()
			);
		}
	}
}
