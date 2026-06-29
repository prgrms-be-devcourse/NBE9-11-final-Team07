package com.back.popspot.domain.goods.dto;

import com.back.popspot.domain.goods.entity.Goods;

public record HostGoodsDetailResponse(
    Long id,
    String name,
    int price,
    int stock,
    String description,
    String productImageUrl,
    String detailImageUrl
) {
    public static HostGoodsDetailResponse from(Goods goods, String productImageUrl, String detailImageUrl) {
        return new HostGoodsDetailResponse(
            goods.getId(),
            goods.getName(),
            goods.getPrice(),
            goods.getStock(),
            goods.getDescription(),
            productImageUrl,
            detailImageUrl
        );
    }
}
