package com.back.popspot.domain.goods.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterResponse;
import com.back.popspot.domain.goods.service.GoodsService;
import com.back.popspot.global.response.CommonApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/host")
public class GoodsController {

    private final GoodsService goodsService;

    @PostMapping("/popups/{popupStoreId}/goods")
    public ResponseEntity<CommonApiResponse<GoodsRegisterResponse>> registerGoods(
        @PathVariable Long popupStoreId,
        @RequestBody @Valid GoodsRegisterRequest request
    ) {
        GoodsRegisterResponse response = goodsService.registerGoods(popupStoreId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonApiResponse.created("굿즈가 등록되었습니다.", response));
    }
}
