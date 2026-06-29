package com.back.popspot.domain.goods.dto;

import java.util.List;

import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GoodsDetailResponse {

    private Long goodsId;
    private String name;
    private String description;
    private int price;
    private int stock;
    private GoodsStatus status;
    private List<GoodsImageResponse> images;
    private Long popupStoreId;
    private String popupStoreTitle;

    public static GoodsDetailResponse from(Goods goods, List<GoodsImageResponse> images) {
        return new GoodsDetailResponse(
            goods.getId(),
            goods.getName(),
            goods.getDescription(),
            goods.getPrice(),
            goods.getStock(),
            goods.getStatus(),
            images,
            goods.getPopupStore().getId(),
            goods.getPopupStore().getTitle()
        );
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class GoodsImageResponse {

        private String imageUrl;
        private GoodsImageType imageType;

        public static GoodsImageResponse from(GoodsImage image, String imageUrl) {
            return new GoodsImageResponse(imageUrl, image.getImageType());
        }
    }
}
