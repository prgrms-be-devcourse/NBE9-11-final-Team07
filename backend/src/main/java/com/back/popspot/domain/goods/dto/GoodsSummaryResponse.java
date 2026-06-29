package com.back.popspot.domain.goods.dto;

import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GoodsSummaryResponse {

    private Long goodsId;
    private String name;
    private int price;
    private String thumbnailImageUrl;
    private int stock;
    private GoodsStatus status;

    public static GoodsSummaryResponse from(Goods goods, String thumbnailImageUrl) {
        return new GoodsSummaryResponse(
            goods.getId(),
            goods.getName(),
            goods.getPrice(),
            thumbnailImageUrl,
            goods.getStock(),
            goods.getStatus()
        );
    }
}
