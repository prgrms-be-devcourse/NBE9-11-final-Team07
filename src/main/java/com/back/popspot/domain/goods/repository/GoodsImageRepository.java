package com.back.popspot.domain.goods.repository;

import java.util.Collection;
import java.util.List;

import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsImageRepository extends JpaRepository<GoodsImage, Long> {

    List<GoodsImage> findByGoods_IdInAndImageTypeOrderByIdAsc(Collection<Long> goodsIds, GoodsImageType imageType);

    List<GoodsImage> findByGoods_IdOrderByIdAsc(Long goodsId);
}
