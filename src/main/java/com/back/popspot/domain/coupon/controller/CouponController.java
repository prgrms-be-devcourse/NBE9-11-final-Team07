package com.back.popspot.domain.coupon.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.coupon.dto.CouponCreateRequest;
import com.back.popspot.domain.coupon.dto.CouponResponse;
import com.back.popspot.domain.coupon.dto.UserCouponResponse;
import com.back.popspot.domain.coupon.service.CouponService;
import com.back.popspot.global.response.CommonApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CouponController {
	private final CouponService couponService;

	@PostMapping("/host/popups/{popupStoreId}/coupons")
	public ResponseEntity<CommonApiResponse<CouponResponse>> createHostCoupon(
		@RequestHeader("X-USER-ID") Long hostUserId,
		@PathVariable Long popupStoreId,
		@Valid @RequestBody CouponCreateRequest request
	) {
		CouponResponse response = couponService.createHostCoupon(hostUserId, popupStoreId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(CommonApiResponse.created("쿠폰이 생성되었습니다.", response));
	}

	@GetMapping("/host/popups/{popupStoreId}/coupons")
	public ResponseEntity<CommonApiResponse<List<CouponResponse>>> getHostCoupons(
		@RequestHeader("X-USER-ID") Long hostUserId,
		@PathVariable Long popupStoreId
	) {
		return ResponseEntity.ok(CommonApiResponse.success(couponService.getHostCoupons(hostUserId, popupStoreId)));
	}

	@DeleteMapping("/host/popups/{popupStoreId}/coupons/{couponId}")
	public ResponseEntity<CommonApiResponse<Void>> deleteHostCoupon(
		@RequestHeader("X-USER-ID") Long hostUserId,
		@PathVariable Long popupStoreId,
		@PathVariable Long couponId
	) {
		couponService.deleteHostCoupon(hostUserId, popupStoreId, couponId);
		return ResponseEntity.ok(CommonApiResponse.successMessage("쿠폰이 삭제되었습니다."));
	}

	@GetMapping("/popups/{popupStoreId}/coupons")
	public ResponseEntity<CommonApiResponse<List<CouponResponse>>> getPublicCoupons(
		@PathVariable Long popupStoreId
	) {
		return ResponseEntity.ok(CommonApiResponse.success(couponService.getPublicCoupons(popupStoreId)));
	}

	@PostMapping("/coupons/{couponId}/issue")
	public ResponseEntity<CommonApiResponse<UserCouponResponse>> issueCoupon(
		@RequestHeader("X-USER-ID") Long userId,
		@PathVariable Long couponId
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(CommonApiResponse.created("쿠폰이 발급되었습니다.", couponService.issueCoupon(userId, couponId)));
	}

	@GetMapping("/me/coupons")
	public ResponseEntity<CommonApiResponse<List<UserCouponResponse>>> getMyCoupons(
		@RequestHeader("X-USER-ID") Long userId
	) {
		return ResponseEntity.ok(CommonApiResponse.success(couponService.getMyCoupons(userId)));
	}
}
