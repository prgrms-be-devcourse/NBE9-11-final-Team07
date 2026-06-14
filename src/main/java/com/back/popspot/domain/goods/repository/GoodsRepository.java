package com.back.popspot.domain.goods.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsStatus;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

    List<Goods> findByPopupStoreUserIdAndStatusNot(Long userId, GoodsStatus status);
}
