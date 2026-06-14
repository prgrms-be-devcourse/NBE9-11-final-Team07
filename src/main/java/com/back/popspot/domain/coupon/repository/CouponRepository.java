package com.back.popspot.domain.coupon.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.back.popspot.domain.coupon.entity.Coupon;
import com.back.popspot.domain.coupon.entity.CouponStatus;

import jakarta.persistence.LockModeType;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
	@EntityGraph(attributePaths = "popupStore")
	List<Coupon> findByPopupStoreIdOrderByCreatedAtDesc(Long popupStoreId);

	@EntityGraph(attributePaths = "popupStore")
	List<Coupon> findByPopupStoreIdAndStatusAndStartedAtLessThanEqualAndExpiredAtAfterOrderByCreatedAtDesc(
		Long popupStoreId,
		CouponStatus status,
		LocalDateTime startedAt,
		LocalDateTime expiredAt
	);

	Optional<Coupon> findByIdAndPopupStoreId(Long id, Long popupStoreId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "popupStore")
	Optional<Coupon> findWithPopupStoreById(Long id);
}
