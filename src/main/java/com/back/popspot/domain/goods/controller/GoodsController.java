package com.back.popspot.domain.goods.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.goods.dto.GoodsDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsImagePresignRequest;
import com.back.popspot.domain.goods.dto.GoodsImagePresignResponse;
import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterResponse;
import com.back.popspot.domain.goods.dto.GoodsSummaryResponse;
import com.back.popspot.domain.goods.dto.GoodsUpdateRequest;
import com.back.popspot.domain.goods.dto.GoodsUpdateResponse;
import com.back.popspot.domain.goods.dto.HostGoodsListResponse;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.service.GoodsService;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.global.response.CommonApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class GoodsController {

	private final GoodsService goodsService;

    // ── 공개 조회 API (/api/v1) ──────────────────────────────────────────────

    @GetMapping("/goods")
    public ResponseEntity<CommonApiResponse<PageResponse<GoodsSummaryResponse>>> getGoodsList(
        @RequestParam(required = false) GoodsStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(CommonApiResponse.success(goodsService.getGoodsList(status, pageable)));
    }

    @GetMapping("/popups/{popupStoreId}/goods")
    public ResponseEntity<CommonApiResponse<PageResponse<GoodsSummaryResponse>>> getGoodsByPopupStore(
        @PathVariable Long popupStoreId,
        @RequestParam(required = false) GoodsStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(
            CommonApiResponse.success(goodsService.getGoodsByPopupStore(popupStoreId, status, pageable))
        );
    }

    @GetMapping("/goods/{goodsId}")
    public ResponseEntity<CommonApiResponse<GoodsDetailResponse>> getGoodsDetail(
        @PathVariable Long goodsId
    ) {
        return ResponseEntity.ok(CommonApiResponse.success(goodsService.getGoodsDetail(goodsId)));
    }

    // ── 호스트 관리 API (/host) ──────────────────────────────────────────────

	@PostMapping("/host/popups/{popupStoreId}/goods")
	public ResponseEntity<CommonApiResponse<GoodsRegisterResponse>> registerHostGoods(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long popupStoreId,
		@RequestBody @Valid GoodsRegisterRequest request
	) {
		GoodsRegisterResponse response = goodsService.registerHostGoods(userId, popupStoreId, request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(CommonApiResponse.created("굿즈가 등록되었습니다.", response));
	}

	@PostMapping("/host/goods/images")
	public ResponseEntity<CommonApiResponse<List<GoodsImagePresignResponse>>> generatePresignedUrls(
		@AuthenticationPrincipal Long userId,
		@RequestBody @Valid GoodsImagePresignRequest request
	) {
		List<GoodsImagePresignResponse> response = goodsService.generatePresignedUrls(request);
		return ResponseEntity.ok(CommonApiResponse.success(response));
	}

	@PatchMapping("/host/goods/{goodsId}")
	public ResponseEntity<CommonApiResponse<GoodsUpdateResponse>> updateHostGoods(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long goodsId,
		@RequestBody @Valid GoodsUpdateRequest request
	) {
		GoodsUpdateResponse response = goodsService.updateHostGoods(userId, goodsId, request);
		return ResponseEntity.ok(CommonApiResponse.success(response));
	}

	@DeleteMapping("/host/goods/{goodsId}")
	public ResponseEntity<CommonApiResponse<Void>> deleteHostGoods(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long goodsId
	) {
		goodsService.deleteHostGoods(userId, goodsId);
		return ResponseEntity.ok(CommonApiResponse.successMessage("굿즈가 삭제되었습니다."));
	}

	@GetMapping("/host/popups/{popupStoreId}/goods")
	public ResponseEntity<CommonApiResponse<List<HostGoodsListResponse>>> getHostGoodsList(
		@AuthenticationPrincipal Long userId,
		@PathVariable Long popupStoreId
	) {
		List<HostGoodsListResponse> response = goodsService.getHostGoodsList(userId, popupStoreId);
		return ResponseEntity.ok(CommonApiResponse.success(response));
	}
}
