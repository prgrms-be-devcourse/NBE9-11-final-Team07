package com.back.popspot.domain.goods.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.GoodsImage;

public interface GoodsImageRepository extends JpaRepository<GoodsImage, Long> {
}
