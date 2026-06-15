package com.back.popspot.domain.goods.dto;

import com.back.popspot.domain.goods.entity.Goods;

public record GoodsUpdateResponse(
    Long id,
    String name,
    int price,
    int stock,
    String description
) {
    public static GoodsUpdateResponse from(Goods goods) {
        return new GoodsUpdateResponse(
            goods.getId(),
            goods.getName(),
            goods.getPrice(),
            goods.getStock(),
            goods.getDescription()
        );
    }
}
