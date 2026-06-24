package com.back.popspot.domain.goods.dto;

import com.back.popspot.domain.goods.entity.Goods;

public record HostGoodsListResponse(
    Long id,
    String name,
    int price,
    int stock,
    String productImageUrl
) {
    public static HostGoodsListResponse from(Goods goods, String productImageUrl) {
        return new HostGoodsListResponse(
            goods.getId(),
            goods.getName(),
            goods.getPrice(),
            goods.getStock(),
            productImageUrl
        );
    }
}
