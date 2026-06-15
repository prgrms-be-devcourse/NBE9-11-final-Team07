package com.back.popspot.domain.goods.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.goods.dto.GoodsOrderCreateRequest;
import com.back.popspot.domain.goods.dto.GoodsOrderCreateResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderRefundResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderSummaryResponse;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;
import com.back.popspot.domain.goods.service.GoodsOrderService;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.global.response.CommonApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GoodsOrderController {

	private final GoodsOrderService goodsOrderService;

	@PostMapping("/goods-orders")
	public ResponseEntity<CommonApiResponse<GoodsOrderCreateResponse>> createOrder(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody GoodsOrderCreateRequest request) {
		GoodsOrderCreateResponse response = goodsOrderService.createOrder(userId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CommonApiResponse.created("생성이 완료되었습니다.", response));
	}

	@PostMapping("/goods-orders/{goodsOrderId}/refund")
	public ResponseEntity<CommonApiResponse<GoodsOrderRefundResponse>> refundOrder(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long goodsOrderId) {
		GoodsOrderRefundResponse response = goodsOrderService.refundOrder(userId, goodsOrderId);
		return ResponseEntity.ok(CommonApiResponse.success(response));
	}

	@GetMapping("/me/goods-orders")
	public ResponseEntity<CommonApiResponse<PageResponse<GoodsOrderSummaryResponse>>> getMyOrders(
			@AuthenticationPrincipal Long userId,
			@RequestParam(required = false) GoodsOrderStatus status,
			@PageableDefault(size = 20) Pageable pageable) {
		PageResponse<GoodsOrderSummaryResponse> response = goodsOrderService.getMyOrders(userId, status, pageable);
		return ResponseEntity.ok(CommonApiResponse.success(response));
	}

	@GetMapping("/goods-orders/{goodsOrderId}")
	public ResponseEntity<CommonApiResponse<GoodsOrderDetailResponse>> getOrderDetail(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long goodsOrderId) {
		GoodsOrderDetailResponse response = goodsOrderService.getOrderDetail(userId, goodsOrderId);
		return ResponseEntity.ok(CommonApiResponse.success(response));
	}
}
