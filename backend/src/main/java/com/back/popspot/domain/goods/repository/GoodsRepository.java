package com.back.popspot.domain.goods.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.back.popspot.domain.goods.entity.GoodsOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

    List<Goods> findByPopupStoreIdAndDeletedAtIsNull(Long popupStoreId);
	Page<Goods> findByDeletedAtIsNull(Pageable pageable);

	Page<Goods> findByStatusAndDeletedAtIsNull(GoodsStatus status, Pageable pageable);

	Page<Goods> findByPopupStore_IdAndDeletedAtIsNull(Long popupStoreId, Pageable pageable);

	Page<Goods> findByPopupStore_IdAndStatusAndDeletedAtIsNull(
		Long popupStoreId, GoodsStatus status, Pageable pageable
	);

	Optional<Goods> findByIdAndDeletedAtIsNull(Long id);

	List<Goods> findByPopupStoreUserIdAndDeletedAtIsNull(Long userId);

	// 재고 차감 — stock >= quantity 조건 포함해 원자적 처리
	// 반환값: 영향받은 행 수 (0이면 재고 부족)
	@Modifying
	@Query("UPDATE Goods g SET g.stock = g.stock - :quantity WHERE g.id = :goodsId AND g.stock >= :quantity")
	int decreaseStock(@Param("goodsId") Long goodsId, @Param("quantity") int quantity);

	// 재고 복구 — 환불/만료 시 호출
	@Modifying
	@Query("UPDATE Goods g SET g.stock = g.stock + :quantity WHERE g.id = :goodsId")
	void increaseStock(@Param("goodsId") Long goodsId, @Param("quantity") int quantity);
}
