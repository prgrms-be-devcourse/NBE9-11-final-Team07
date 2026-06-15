package com.back.popspot.domain.goods.dto;

import com.back.popspot.domain.goods.entity.Goods;

public record GoodsListResponse(
    Long id,
    String name,
    int price,
    int stock
) {
    public static GoodsListResponse from(Goods goods) {
        return new GoodsListResponse(
            goods.getId(),
            goods.getName(),
            goods.getPrice(),
            goods.getStock()
        );
    }
}
