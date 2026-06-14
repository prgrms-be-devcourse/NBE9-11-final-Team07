package com.back.popspot.domain.goods.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;

public interface GoodsImageRepository extends JpaRepository<GoodsImage, Long> {

	Optional<GoodsImage> findFirstByGoods_IdAndImageTypeOrderByIdAsc(Long goodsId, GoodsImageType imageType);
}
