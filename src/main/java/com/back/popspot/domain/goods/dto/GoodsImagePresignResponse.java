package com.back.popspot.domain.goods.dto;

public record GoodsImagePresignResponse(
    String imageKey,
    String presignedUrl
) {
}
