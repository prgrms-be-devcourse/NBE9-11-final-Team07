package com.back.popspot.domain.coupon.dto;

import java.time.LocalDateTime;

import com.back.popspot.domain.coupon.entity.Coupon;
import com.back.popspot.domain.coupon.entity.CouponDiscountType;
import com.back.popspot.domain.coupon.entity.UserCoupon;
import com.back.popspot.domain.coupon.entity.UserCouponStatus;

public record UserCouponResponse(
	Long id,
	Long couponId,
	Long popupStoreId,
	String popupStoreTitle,
	String name,
	CouponDiscountType discountType,
	int discountValue,
	Integer maxDiscountAmount,
	Integer minOrderAmount,
	UserCouponStatus status,
	LocalDateTime expiredAt,
	LocalDateTime usedAt,
	LocalDateTime issuedAt
) {
	public static UserCouponResponse from(UserCoupon userCoupon) {
		Coupon coupon = userCoupon.getCoupon();

		return new UserCouponResponse(
			userCoupon.getId(),
			coupon.getId(),
			coupon.getPopupStore().getId(),
			coupon.getPopupStore().getTitle(),
			coupon.getName(),
			coupon.getDiscountType(),
			coupon.getDiscountValue(),
			coupon.getMaxDiscountAmount(),
			coupon.getMinOrderAmount(),
			userCoupon.getStatus(),
			coupon.getExpiredAt(),
			userCoupon.getUsedAt(),
			userCoupon.getCreatedAt()
		);
	}
}
