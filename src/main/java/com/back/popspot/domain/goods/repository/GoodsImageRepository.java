package com.back.popspot.domain.goods.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;

public interface GoodsImageRepository extends JpaRepository<GoodsImage, Long> {

	Optional<GoodsImage> findFirstByGoods_IdAndImageTypeOrderByIdAsc(Long goodsId, GoodsImageType imageType);

	List<GoodsImage> findByGoods_IdInAndImageTypeOrderByIdAsc(Collection<Long> goodsIds, GoodsImageType imageType);

	List<GoodsImage> findByGoods_IdOrderByIdAsc(Long goodsId);
}
