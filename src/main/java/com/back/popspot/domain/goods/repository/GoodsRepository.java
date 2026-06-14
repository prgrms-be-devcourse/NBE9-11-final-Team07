package com.back.popspot.domain.goods.repository;

import java.util.Optional;

import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

    Page<Goods> findByDeletedAtIsNull(Pageable pageable);

    Page<Goods> findByStatusAndDeletedAtIsNull(GoodsStatus status, Pageable pageable);

    Page<Goods> findByPopupStore_IdAndDeletedAtIsNull(Long popupStoreId, Pageable pageable);

    Page<Goods> findByPopupStore_IdAndStatusAndDeletedAtIsNull(
        Long popupStoreId, GoodsStatus status, Pageable pageable
    );

    Optional<Goods> findByIdAndDeletedAtIsNull(Long id);
}
