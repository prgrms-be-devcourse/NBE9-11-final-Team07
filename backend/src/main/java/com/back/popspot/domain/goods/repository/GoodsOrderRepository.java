package com.back.popspot.domain.goods.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GoodsOrderRepository extends JpaRepository<GoodsOrder, Long> {

	Page<GoodsOrder> findByUser_Id(Long userId, Pageable pageable);

	Page<GoodsOrder> findByUser_IdAndStatus(Long userId, GoodsOrderStatus status, Pageable pageable);

	// 스케줄러용 — PENDING이면서 만료 시각이 지난 주문 조회
	@Query("SELECT o FROM GoodsOrder o WHERE o.status = 'PENDING' AND o.expiresAt <= :now")
	List<GoodsOrder> findExpiredPendingOrders(@Param("now") LocalDateTime now);
}
