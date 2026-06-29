package com.back.popspot.domain.goods.dto;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;

import lombok.Getter;

@Getter
public class GoodsOrderRefundResponse {

	private final Long goodsOrderId;
	private final GoodsOrderStatus status;

	private GoodsOrderRefundResponse(Long goodsOrderId, GoodsOrderStatus status) {
		this.goodsOrderId = goodsOrderId;
		this.status = status;
	}

	public static GoodsOrderRefundResponse from(GoodsOrder order) {
		return new GoodsOrderRefundResponse(order.getId(), order.getStatus());
	}
}
