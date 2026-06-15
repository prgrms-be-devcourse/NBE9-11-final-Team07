package com.back.popspot.domain.coupon.dto;

import java.time.LocalDateTime;

import com.back.popspot.domain.coupon.entity.CouponDiscountType;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CouponCreateRequest(
	@NotBlank
	@Size(max = 100)
	String name,

	@NotNull
	CouponDiscountType discountType,

	@Min(1)
	int discountValue,

	@Min(0)
	Integer maxDiscountAmount,

	@Min(0)
	Integer minOrderAmount,

	@Min(1)
	int totalQuantity,

	@NotNull
	@FutureOrPresent
	LocalDateTime startedAt,

	@NotNull
	@Future
	LocalDateTime expiredAt
) {
}
