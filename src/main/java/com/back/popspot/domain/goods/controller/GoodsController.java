package com.back.popspot.domain.goods.controller;

import com.back.popspot.domain.goods.dto.GoodsDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsSummaryResponse;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.service.GoodsService;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.global.response.CommonApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

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
}
