package com.back.popspot.domain.goods.dto;

import java.util.List;

import com.back.popspot.domain.goods.entity.GoodsImageType;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record GoodsImagePresignRequest(
    @NotNull GoodsImageType imageType,
    @NotEmpty List<@NotNull String> fileNames
) {
}
