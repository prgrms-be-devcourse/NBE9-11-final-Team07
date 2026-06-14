package com.back.popspot.domain.goods.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.goods.entity.Goods;

public interface GoodsRepository extends JpaRepository<Goods, Long> {

	Optional<Goods> findByIdAndDeletedAtIsNull(Long id);
}
