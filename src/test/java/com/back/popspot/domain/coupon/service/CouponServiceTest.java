package com.back.popspot.domain.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.coupon.dto.CouponCreateRequest;
import com.back.popspot.domain.coupon.dto.CouponResponse;
import com.back.popspot.domain.coupon.dto.UserCouponResponse;
import com.back.popspot.domain.coupon.entity.Coupon;
import com.back.popspot.domain.coupon.entity.CouponDiscountType;
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

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

	@Mock
	private CouponRepository couponRepository;

	@Mock
	private UserCouponRepository userCouponRepository;

	@Mock
	private PopupStoreRepository popupStoreRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private CouponService couponService;

	@Test
	void 호스트가_소유한_팝업스토어에_쿠폰을_생성한다() {
		Long hostUserId = 1L;
		Long popupStoreId = 10L;
		PopupStore popupStore = createPopupStore(popupStoreId, hostUserId);
		CouponCreateRequest request = createRequest(
			LocalDateTime.now().plusDays(1),
			LocalDateTime.now().plusDays(10),
			100
		);

		given(popupStoreRepository.findById(popupStoreId)).willReturn(Optional.of(popupStore));
		given(couponRepository.save(org.mockito.ArgumentMatchers.any(Coupon.class)))
			.willAnswer(invocation -> {
				Coupon coupon = invocation.getArgument(0);
				ReflectionTestUtils.setField(coupon, "id", 100L);
				ReflectionTestUtils.setField(coupon, "createdAt", LocalDateTime.now());
				return coupon;
			});

		CouponResponse response = couponService.createHostCoupon(hostUserId, popupStoreId, request);

		assertThat(response.id()).isEqualTo(100L);
		assertThat(response.popupStoreId()).isEqualTo(popupStoreId);
		assertThat(response.name()).isEqualTo("신규 가입 쿠폰");
		assertThat(response.status()).isEqualTo(CouponStatus.ACTIVE);
		assertThat(response.remainingQuantity()).isEqualTo(100);
	}

	@Test
	void 쿠폰_만료일이_시작일보다_빠르면_생성할_수_없다() {
		LocalDateTime startedAt = LocalDateTime.now().plusDays(10);
		CouponCreateRequest request = createRequest(startedAt, startedAt.minusDays(1), 100);

		assertThatThrownBy(() -> couponService.createHostCoupon(1L, 10L, request))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
		verify(popupStoreRepository, never()).findById(10L);
	}

	@Test
	void 팝업스토어_소유자가_아니면_쿠폰을_생성할_수_없다() {
		Long popupStoreId = 10L;
		PopupStore popupStore = createPopupStore(popupStoreId, 2L);
		CouponCreateRequest request = createRequest(
			LocalDateTime.now().plusDays(1),
			LocalDateTime.now().plusDays(10),
			100
		);
		given(popupStoreRepository.findById(popupStoreId)).willReturn(Optional.of(popupStore));

		assertThatThrownBy(() -> couponService.createHostCoupon(1L, popupStoreId, request))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
		verify(couponRepository, never()).save(org.mockito.ArgumentMatchers.any(Coupon.class));
	}

	@Test
	void 이미_발급받은_쿠폰은_중복_발급할_수_없다() {
		Long userId = 1L;
		Long couponId = 100L;
		User user = createUser(userId);
		Coupon coupon = createCoupon(couponId, 10L, 2);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(couponRepository.findWithPopupStoreById(couponId)).willReturn(Optional.of(coupon));
		given(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).willReturn(true);

		assertThatThrownBy(() -> couponService.issueCoupon(userId, couponId))
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
		verify(userCouponRepository, never()).save(org.mockito.ArgumentMatchers.any(UserCoupon.class));
	}

	@Test
	void 쿠폰을_발급하면_발급_수량이_증가한다() {
		Long userId = 1L;
		Long couponId = 100L;
		User user = createUser(userId);
		Coupon coupon = createCoupon(couponId, 10L, 2);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(couponRepository.findWithPopupStoreById(couponId)).willReturn(Optional.of(coupon));
		given(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).willReturn(false);
		given(userCouponRepository.save(org.mockito.ArgumentMatchers.any(UserCoupon.class)))
			.willAnswer(invocation -> {
				UserCoupon userCoupon = invocation.getArgument(0);
				ReflectionTestUtils.setField(userCoupon, "id", 200L);
				ReflectionTestUtils.setField(userCoupon, "createdAt", LocalDateTime.now());
				return userCoupon;
			});

		UserCouponResponse response = couponService.issueCoupon(userId, couponId);

		assertThat(response.id()).isEqualTo(200L);
		assertThat(response.couponId()).isEqualTo(couponId);
		assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
		assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
	}

	@Test
	void 마지막_수량을_발급하면_쿠폰이_품절된다() {
		Long userId = 1L;
		Long couponId = 100L;
		User user = createUser(userId);
		Coupon coupon = createCoupon(couponId, 10L, 1);
		given(userRepository.findById(userId)).willReturn(Optional.of(user));
		given(couponRepository.findWithPopupStoreById(couponId)).willReturn(Optional.of(coupon));
		given(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).willReturn(false);
		given(userCouponRepository.save(org.mockito.ArgumentMatchers.any(UserCoupon.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		couponService.issueCoupon(userId, couponId);

		assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
		assertThat(coupon.getStatus()).isEqualTo(CouponStatus.SOLDOUT);
	}

	private CouponCreateRequest createRequest(LocalDateTime startedAt, LocalDateTime expiredAt, int totalQuantity) {
		return new CouponCreateRequest(
			"신규 가입 쿠폰",
			CouponDiscountType.AMOUNT,
			1000,
			null,
			5000,
			totalQuantity,
			startedAt,
			expiredAt
		);
	}

	private Coupon createCoupon(Long couponId, Long popupStoreId, int totalQuantity) {
		PopupStore popupStore = createPopupStore(popupStoreId, 2L);
		Coupon coupon = Coupon.create(
			popupStore,
			createRequest(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(10), totalQuantity)
		);
		ReflectionTestUtils.setField(coupon, "id", couponId);
		ReflectionTestUtils.setField(coupon, "createdAt", LocalDateTime.now());
		return coupon;
	}

	private PopupStore createPopupStore(Long popupStoreId, Long ownerId) {
		User owner = createUser(ownerId);
		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "id", popupStoreId);
		ReflectionTestUtils.setField(popupStore, "user", owner);
		ReflectionTestUtils.setField(popupStore, "title", "테스트 팝업");
		return popupStore;
	}

	private User createUser(Long userId) {
		User user = new User();
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}
}
