package com.back.popspot.domain.coupon.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.back.popspot.domain.coupon.dto.CouponCreateRequest;
import com.back.popspot.domain.coupon.entity.Coupon;
import com.back.popspot.domain.coupon.entity.CouponDiscountType;
import com.back.popspot.domain.coupon.entity.CouponStatus;
import com.back.popspot.domain.coupon.repository.CouponRepository;
import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.security.jwt.JwtTokenProvider;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("쿠폰 통합 테스트")
class CouponIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PopupStoreRepository popupStoreRepository;

	@Autowired
	private CouponRepository couponRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	@DisplayName("호스트가 쿠폰을 생성하면 DB에 쿠폰이 저장된다")
	void createHostCoupon_savesCoupon() throws Exception {
		User host = userRepository.save(User.create("host@test.com", "host"));
		PopupStore popupStore = popupStoreRepository.save(PopupStore.of(host, popupStoreCreateRequest()));
		String accessToken = jwtTokenProvider.createAccessToken(host.getId(), host.getEmail(), host.getName());

		CouponCreateRequest request = new CouponCreateRequest(
			"신규 가입 쿠폰",
			CouponDiscountType.AMOUNT,
			1000,
			null,
			5000,
			100,
			LocalDateTime.now().plusMinutes(1),
			LocalDateTime.now().plusDays(7)
		);

		mockMvc.perform(post("/host/popups/{popupStoreId}/coupons", popupStore.getId())
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("쿠폰이 생성되었습니다."))
			.andExpect(jsonPath("$.data.name").value("신규 가입 쿠폰"))
			.andExpect(jsonPath("$.data.popupStoreId").value(popupStore.getId()));

		List<Coupon> coupons = couponRepository.findAll();

		assertThat(coupons).hasSize(1);
		assertThat(coupons.getFirst().getPopupStore().getId()).isEqualTo(popupStore.getId());
		assertThat(coupons.getFirst().getName()).isEqualTo("신규 가입 쿠폰");
		assertThat(coupons.getFirst().getDiscountType()).isEqualTo(CouponDiscountType.AMOUNT);
		assertThat(coupons.getFirst().getDiscountValue()).isEqualTo(1000);
		assertThat(coupons.getFirst().getMinOrderAmount()).isEqualTo(5000);
		assertThat(coupons.getFirst().getTotalQuantity()).isEqualTo(100);
		assertThat(coupons.getFirst().getIssuedQuantity()).isZero();
		assertThat(coupons.getFirst().getStatus()).isEqualTo(CouponStatus.ACTIVE);
	}

	private PopupStoreCreateRequest popupStoreCreateRequest() {
		LocalDateTime now = LocalDateTime.now();
		return new PopupStoreCreateRequest(
			"테스트 팝업",
			"서울",
			PopupFeeType.FREE,
			null,
			now.minusDays(1),
			now.plusDays(10),
			now.plusDays(1),
			now.plusDays(5),
			null,
			"설명"
		);
	}
}
