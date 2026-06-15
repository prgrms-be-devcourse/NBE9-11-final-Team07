package com.back.popspot.domain.goods.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsStatus;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

    Page<Goods> findByDeletedAtIsNull(Pageable pageable);

    Page<Goods> findByStatusAndDeletedAtIsNull(GoodsStatus status, Pageable pageable);

    Page<Goods> findByPopupStore_IdAndDeletedAtIsNull(Long popupStoreId, Pageable pageable);

    Page<Goods> findByPopupStore_IdAndStatusAndDeletedAtIsNull(
        Long popupStoreId, GoodsStatus status, Pageable pageable
    );

    Optional<Goods> findByIdAndDeletedAtIsNull(Long id);

    List<Goods> findByPopupStoreUserIdAndDeletedAtIsNull(Long userId);
}
