package com.back.popspot.domain.coupon.entity;

import java.time.LocalDateTime;

import com.back.popspot.domain.coupon.dto.CouponCreateRequest;
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
@Table(name = "coupon")
public class Coupon extends BaseEntity {
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "popup_store_id", nullable = false)
	private PopupStore popupStore;

	@Column(length = 100, nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "discount_type", nullable = false)
	private CouponDiscountType discountType;

	@Column(name = "discount_value", nullable = false)
	private int discountValue;

	@Column(name = "max_discount_amount")
	private Integer maxDiscountAmount;

	@Column(name = "min_order_amount")
	private Integer minOrderAmount;

	@Column(name = "total_quantity", nullable = false)
	private int totalQuantity;

	@Column(name = "issued_quantity", nullable = false)
	private int issuedQuantity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CouponStatus status;

	@Column(name = "expired_at", nullable = false)
	private LocalDateTime expiredAt;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	public static Coupon create(PopupStore popupStore, CouponCreateRequest request) {
		Coupon coupon = new Coupon();
		coupon.popupStore = popupStore;
		coupon.name = request.name();
		coupon.discountType = request.discountType();
		coupon.discountValue = request.discountValue();
		coupon.maxDiscountAmount = request.maxDiscountAmount();
		coupon.minOrderAmount = request.minOrderAmount();
		coupon.totalQuantity = request.totalQuantity();
		coupon.issuedQuantity = 0;
		coupon.status = CouponStatus.ACTIVE;
		coupon.expiredAt = request.expiredAt();
		coupon.startedAt = request.startedAt();
		return coupon;
	}

	public boolean isIssuable(LocalDateTime now) {
		return status == CouponStatus.ACTIVE
			&& !startedAt.isAfter(now)
			&& expiredAt.isAfter(now)
			&& issuedQuantity < totalQuantity;
	}

	public void issue() {
		issuedQuantity++;
		if (issuedQuantity >= totalQuantity) {
			status = CouponStatus.SOLDOUT;
		}
	}
}
