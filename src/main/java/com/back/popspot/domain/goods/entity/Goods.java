package com.back.popspot.domain.goods.entity;

import java.time.LocalDateTime;

import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "goods")
public class Goods extends BaseEntity {

    public static Goods register(PopupStore popupStore, String name, int price, int stock, String description) {
        Goods goods = new Goods();
        goods.popupStore = popupStore;
        goods.name = name;
        goods.price = price;
        goods.stock = stock;
        goods.description = description;
        goods.status = GoodsStatus.READY;
        return goods;
    }
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "popup_store_id", nullable = false)
	private PopupStore popupStore;

	@Column(length = 50, nullable = false)
	private String name;

	@Column(nullable = false)
	private int price;

	@Column(nullable = false)
	private int stock;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GoodsStatus status;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

    public void update(String name, Integer price, Integer stock, String description) {
        if (name != null) this.name = name;
        if (price != null) this.price = price;
        if (stock != null) this.stock = stock;
        if (description != null) this.description = description;
    }
}
