package com.back.popspot.domain.goods.dto;

import jakarta.validation.constraints.Min;

public record GoodsUpdateRequest(
    String name,
    @Min(0) Integer price,
    @Min(0) Integer stock,
    String description
) {
}
