package com.back.popspot.domain.goods.entity;

import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "goods_order_item")
public class GoodsOrderItem extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "goods_id", nullable = false)
	private Goods goods;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "goods_order_id", nullable = false)
	private GoodsOrder goodsOrder;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false)
	private int unitPrice;

	@Column(name = "item_amount", nullable = false)
	private int itemAmount;
}
