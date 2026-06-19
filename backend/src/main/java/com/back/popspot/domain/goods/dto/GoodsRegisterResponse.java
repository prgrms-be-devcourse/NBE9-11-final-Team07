package com.back.popspot.domain.goods.dto;

import com.back.popspot.domain.goods.entity.Goods;

public record GoodsRegisterResponse(
    Long id,
    Long popupStoreId,
    String name,
    int price,
    int stock,
    String description
) {
    public static GoodsRegisterResponse from(Goods goods) {
        return new GoodsRegisterResponse(
            goods.getId(),
            goods.getPopupStore().getId(),
            goods.getName(),
            goods.getPrice(),
            goods.getStock(),
            goods.getDescription()
        );
    }
}
