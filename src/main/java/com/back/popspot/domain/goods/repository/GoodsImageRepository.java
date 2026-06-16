package com.back.popspot.domain.goods.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsImage;

public interface GoodsImageRepository extends JpaRepository<GoodsImage, Long> {

    List<GoodsImage> findByGoods(Goods goods);

    List<GoodsImage> findByGoodsIn(List<Goods> goods);
}
