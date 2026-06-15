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

	// 쿠폰 발급 시 쿠폰 자체의 수량만 제어하면 되므로 락 획득 시점에는 @EntityGraph 없이 쿠폰 조회
	// 이후 필요한 시점에 Lazy Loading으로 팝업스토어 정보를 가져옴
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Coupon> findWithPopupStoreById(Long id);
}
