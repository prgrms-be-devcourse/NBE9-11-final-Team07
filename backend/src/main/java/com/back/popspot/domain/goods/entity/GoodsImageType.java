package com.back.popspot.domain.goods.entity;

public enum GoodsImageType {
    PRODUCT, DETAIL;

    public String code() {
        return name().toLowerCase();
    }
}
