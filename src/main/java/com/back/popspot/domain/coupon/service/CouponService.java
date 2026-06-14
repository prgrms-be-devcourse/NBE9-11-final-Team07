package com.back.popspot.domain.coupon.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.back.popspot.domain.coupon.dto.CouponCreateRequest;
import com.back.popspot.domain.coupon.dto.CouponResponse;
import com.back.popspot.domain.coupon.dto.UserCouponResponse;
import com.back.popspot.domain.coupon.entity.Coupon;
import com.back.popspot.domain.coupon.entity.CouponStatus;
import com.back.popspot.domain.coupon.entity.UserCoupon;
import com.back.popspot.domain.coupon.repository.CouponRepository;
import com.back.popspot.domain.coupon.repository.UserCouponRepository;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {
	private final CouponRepository couponRepository;
	private final UserCouponRepository userCouponRepository;
	private final PopupStoreRepository popupStoreRepository;
	private final UserRepository userRepository;

	// 호스트가 소유한 팝업스토어에 새로운 쿠폰을 생성
	@Transactional
	public CouponResponse createHostCoupon(Long hostUserId, Long popupStoreId, CouponCreateRequest request) {
		validatePeriod(request.startedAt(), request.expiredAt());
		PopupStore popupStore = getPopupStore(popupStoreId);
		validateHostOwner(hostUserId, popupStore);

		Coupon coupon = couponRepository.save(Coupon.create(popupStore, request));
		return CouponResponse.from(coupon);
	}

	// 호스트가 소유한 팝업스토어의 전체 쿠폰을 최신순으로 조회
	public List<CouponResponse> getHostCoupons(Long hostUserId, Long popupStoreId) {
		PopupStore popupStore = getPopupStore(popupStoreId);
		validateHostOwner(hostUserId, popupStore);

		return couponRepository.findByPopupStoreIdOrderByCreatedAtDesc(popupStoreId)
			.stream()
			.map(CouponResponse::from)
			.toList();
	}

	// 호스트가 소유한 팝업스토어의 특정 쿠폰을 삭제
	@Transactional
	public void deleteHostCoupon(Long hostUserId, Long popupStoreId, Long couponId) {
		PopupStore popupStore = getPopupStore(popupStoreId);
		validateHostOwner(hostUserId, popupStore);

		Coupon coupon = couponRepository.findByIdAndPopupStoreId(couponId, popupStoreId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		couponRepository.delete(coupon);
	}

	// 사용자가 현재 발급받을 수 있는 팝업스토어 쿠폰을 조회
	public List<CouponResponse> getPublicCoupons(Long popupStoreId) {
		if (!popupStoreRepository.existsById(popupStoreId)) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}

		LocalDateTime now = LocalDateTime.now();
		return couponRepository
			.findByPopupStoreIdAndStatusAndStartedAtLessThanEqualAndExpiredAtAfterOrderByCreatedAtDesc(
				popupStoreId,
				CouponStatus.ACTIVE,
				now,
				now
			)
			.stream()
			.filter(coupon -> coupon.getIssuedQuantity() < coupon.getTotalQuantity())
			.map(CouponResponse::from)
			.toList();
	}

	// 사용자에게 쿠폰을 발급하고 쿠폰의 발급 수량을 갱신
	@Transactional
	public UserCouponResponse issueCoupon(Long userId, Long couponId) {
		User user = getUser(userId);
		Coupon coupon = couponRepository.findWithPopupStoreById(couponId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
			throw new BusinessException(ErrorCode.CONFLICT);
		}

		if (!coupon.isIssuable(LocalDateTime.now())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		coupon.issue();
		UserCoupon userCoupon = userCouponRepository.save(UserCoupon.issue(user, coupon));
		return UserCouponResponse.from(userCoupon);
	}

	// 사용자가 발급받은 쿠폰을 최신순으로 조회
	public List<UserCouponResponse> getMyCoupons(Long userId) {
		if (!userRepository.existsById(userId)) {
			throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
		}

		return userCouponRepository.findByUserIdOrderByCreatedAtDesc(userId)
			.stream()
			.map(UserCouponResponse::from)
			.toList();
	}

	// 팝업스토어를 조회하고 존재하지 않으면 예외를 발생
	private PopupStore getPopupStore(Long popupStoreId) {
		return popupStoreRepository.findById(popupStoreId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	// 사용자를 조회하고 존재하지 않으면 예외를 발생
	private User getUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	// 요청한 호스트가 팝업스토어의 소유자인지 검증
	private void validateHostOwner(Long hostUserId, PopupStore popupStore) {
		if (!popupStore.getUser().getId().equals(hostUserId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}

	// 쿠폰 만료일이 시작일보다 이후인지 검증
	private void validatePeriod(LocalDateTime startedAt, LocalDateTime expiredAt) {
		if (!expiredAt.isAfter(startedAt)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
	}
}
