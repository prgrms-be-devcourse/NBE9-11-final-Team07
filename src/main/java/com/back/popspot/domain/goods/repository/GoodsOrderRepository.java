package com.back.popspot.domain.goods.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;

public interface GoodsOrderRepository extends JpaRepository<GoodsOrder, Long> {

	Page<GoodsOrder> findByUser_Id(Long userId, Pageable pageable);

	Page<GoodsOrder> findByUser_IdAndStatus(Long userId, GoodsOrderStatus status, Pageable pageable);
}
