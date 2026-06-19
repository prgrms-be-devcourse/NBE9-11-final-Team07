package com.back.popspot.domain.coupon.dto;

import java.time.LocalDateTime;

import com.back.popspot.domain.coupon.entity.Coupon;
import com.back.popspot.domain.coupon.entity.CouponDiscountType;
import com.back.popspot.domain.coupon.entity.CouponStatus;

public record CouponResponse(
	Long id,
	Long popupStoreId,
	String popupStoreTitle,
	String name,
	CouponDiscountType discountType,
	int discountValue,
	Integer maxDiscountAmount,
	Integer minOrderAmount,
	int totalQuantity,
	int issuedQuantity,
	int remainingQuantity,
	CouponStatus status,
	LocalDateTime startedAt,
	LocalDateTime expiredAt,
	LocalDateTime createdAt
) {
	public static CouponResponse from(Coupon coupon) {
		return new CouponResponse(
			coupon.getId(),
			coupon.getPopupStore().getId(),
			coupon.getPopupStore().getTitle(),
			coupon.getName(),
			coupon.getDiscountType(),
			coupon.getDiscountValue(),
			coupon.getMaxDiscountAmount(),
			coupon.getMinOrderAmount(),
			coupon.getTotalQuantity(),
			coupon.getIssuedQuantity(),
			Math.max(coupon.getTotalQuantity() - coupon.getIssuedQuantity(), 0),
			coupon.getStatus(),
			coupon.getStartedAt(),
			coupon.getExpiredAt(),
			coupon.getCreatedAt()
		);
	}
}
