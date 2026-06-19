package com.back.popspot.domain.goods.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.GoodsOrderItem;

public interface GoodsOrderItemRepository extends JpaRepository<GoodsOrderItem, Long> {

	List<GoodsOrderItem> findByGoodsOrder_Id(Long goodsOrderId);

	List<GoodsOrderItem> findByGoodsOrder_IdIn(Collection<Long> goodsOrderIds);
}
