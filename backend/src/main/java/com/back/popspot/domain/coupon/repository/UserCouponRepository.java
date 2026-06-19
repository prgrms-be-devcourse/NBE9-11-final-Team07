package com.back.popspot.domain.coupon.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.back.popspot.domain.coupon.entity.UserCoupon;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {
	boolean existsByUserIdAndCouponId(Long userId, Long couponId);

	@EntityGraph(attributePaths = {"coupon", "coupon.popupStore"})
	List<UserCoupon> findByUserIdOrderByCreatedAtDesc(Long userId);
}
