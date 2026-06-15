package com.back.popspot.domain.coupon.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.back.popspot.domain.coupon.dto.CouponCreateRequest;
import com.back.popspot.domain.coupon.dto.CouponResponse;
import com.back.popspot.domain.coupon.dto.UserCouponResponse;
import com.back.popspot.domain.coupon.entity.CouponDiscountType;
import com.back.popspot.domain.coupon.entity.CouponStatus;
import com.back.popspot.domain.coupon.entity.UserCouponStatus;
import com.back.popspot.domain.coupon.service.CouponService;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.exception.GlobalExceptionHandler;

@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

	@Mock
	private CouponService couponService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new CouponController(couponService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.setCustomArgumentResolvers(new AuthenticationPrincipalResolver())
			.build();
	}

	@Test
	@DisplayName("주최자가 쿠폰 생성 시 201 반환")
	void test1() throws Exception {
		Long hostUserId = 1L;
		Long popupStoreId = 10L;
		CouponCreateRequest request = validRequest();
		CouponResponse response = couponResponse(popupStoreId);
		given(couponService.createHostCoupon(hostUserId, popupStoreId, request)).willReturn(response);

		mockMvc.perform(post("/host/popups/{popupStoreId}/coupons", popupStoreId)
				.principal(authentication(hostUserId))
				.contentType(MediaType.APPLICATION_JSON)
				.content(toJson(request)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("쿠폰이 생성되었습니다."))
			.andExpect(jsonPath("$.data.id").value(100L))
			.andExpect(jsonPath("$.data.popupStoreId").value(popupStoreId))
			.andExpect(jsonPath("$.data.name").value("신규 가입 쿠폰"));

		verify(couponService).createHostCoupon(hostUserId, popupStoreId, request);
	}

	@Test
	@DisplayName("쿠폰 생성 요청이 유효하지 않으면 400 반환")
	void test2() throws Exception {
		CouponCreateRequest request = new CouponCreateRequest(
			"",
			CouponDiscountType.AMOUNT,
			0,
			null,
			0,
			0,
			LocalDateTime.now().plusDays(1),
			LocalDateTime.now().plusDays(10)
		);

		mockMvc.perform(post("/host/popups/{popupStoreId}/coupons", 10L)
				.principal(authentication(1L))
				.contentType(MediaType.APPLICATION_JSON)
				.content(toJson(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
			.andExpect(jsonPath("$.message").value("잘못된 요청입니다."));
	}

	@Test
	@DisplayName("발급 가능한 쿠폰 목록 조회")
	void test3() throws Exception {
		Long popupStoreId = 10L;
		given(couponService.getPublicCoupons(popupStoreId)).willReturn(List.of(couponResponse(popupStoreId)));

		mockMvc.perform(get("/popups/{popupStoreId}/coupons", popupStoreId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].remainingQuantity").value(100));
	}

	@Test
	@DisplayName("사용자에게 쿠폰 발급 시 201 반환")
	void test4() throws Exception {
		Long userId = 1L;
		Long couponId = 100L;
		UserCouponResponse response = new UserCouponResponse(
			200L,
			couponId,
			10L,
			"테스트 팝업",
			"신규 가입 쿠폰",
			CouponDiscountType.AMOUNT,
			1000,
			null,
			5000,
			UserCouponStatus.ISSUED,
			LocalDateTime.now().plusDays(10),
			null,
			LocalDateTime.now()
		);
		given(couponService.issueCoupon(userId, couponId)).willReturn(response);

		mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
				.principal(authentication(userId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("쿠폰이 발급되었습니다."))
			.andExpect(jsonPath("$.data.id").value(200L))
			.andExpect(jsonPath("$.data.status").value("ISSUED"));
	}

	@Test
	@DisplayName("중복 쿠폰 발급 요청 시 409 반환")
	void test5() throws Exception {
		Long userId = 1L;
		Long couponId = 100L;
		given(couponService.issueCoupon(userId, couponId))
			.willThrow(new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED));

		mockMvc.perform(post("/coupons/{couponId}/issue", couponId)
				.principal(authentication(userId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICT"))
			.andExpect(jsonPath("$.message").value("이미 처리된 요청입니다."))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	private CouponCreateRequest validRequest() {
		return new CouponCreateRequest(
			"신규 가입 쿠폰",
			CouponDiscountType.AMOUNT,
			1000,
			null,
			5000,
			100,
			LocalDateTime.now().plusDays(1),
			LocalDateTime.now().plusDays(10)
		);
	}

	private UsernamePasswordAuthenticationToken authentication(Long userId) {
		return new UsernamePasswordAuthenticationToken(userId, null, List.of());
	}

	private static class AuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {
		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
		}

		@Override
		public Object resolveArgument(
			MethodParameter parameter,
			ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest,
			WebDataBinderFactory binderFactory
		) {
			Authentication authentication = (Authentication)webRequest.getUserPrincipal();
			return authentication.getPrincipal();
		}
	}

	private CouponResponse couponResponse(Long popupStoreId) {
		return new CouponResponse(
			100L,
			popupStoreId,
			"테스트 팝업",
			"신규 가입 쿠폰",
			CouponDiscountType.AMOUNT,
			1000,
			null,
			5000,
			100,
			0,
			100,
			CouponStatus.ACTIVE,
			LocalDateTime.now().plusDays(1),
			LocalDateTime.now().plusDays(10),
			LocalDateTime.now()
		);
	}

	private String toJson(CouponCreateRequest request) {
		return """
			{
			  "name": "%s",
			  "discountType": "%s",
			  "discountValue": %d,
			  "maxDiscountAmount": %s,
			  "minOrderAmount": %s,
			  "totalQuantity": %d,
			  "startedAt": "%s",
			  "expiredAt": "%s"
			}
			""".formatted(
			request.name(),
			request.discountType(),
			request.discountValue(),
			request.maxDiscountAmount(),
			request.minOrderAmount(),
			request.totalQuantity(),
			request.startedAt(),
			request.expiredAt()
		);
	}
}
