package com.back.popspot.domain.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.back.popspot.global.response.CommonApiResponse;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private static final String ACCESS_TOKEN_COOKIE = "access_token";
	private static final String LOGGED_IN_COOKIE = "logged_in";

	@Value("${app.cookie.secure:false}")
	private boolean cookieSecure;

	@Value("${app.cookie.same-site:Lax}")
	private String cookieSameSite;

	@GetMapping("/me")
	public CommonApiResponse<Long> me(@AuthenticationPrincipal Long userId) {
		return CommonApiResponse.success(userId);
	}

	/**
	 * 로그아웃: HttpOnly access_token 쿠키와 읽기용 logged_in 플래그 쿠키를 만료시킨다.
	 * HttpOnly 쿠키는 JS 로 삭제할 수 없으므로 서버가 만료(Max-Age=0) 처리한다.
	 */
	@PostMapping("/logout")
	public CommonApiResponse<Void> logout(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, expire(ACCESS_TOKEN_COOKIE, true).toString());
		response.addHeader(HttpHeaders.SET_COOKIE, expire(LOGGED_IN_COOKIE, false).toString());
		return CommonApiResponse.successMessage("로그아웃 되었습니다.");
	}

	private ResponseCookie expire(String name, boolean httpOnly) {
		return ResponseCookie.from(name, "")
				.httpOnly(httpOnly)
				.secure(cookieSecure)
				.path("/")
				.sameSite(cookieSameSite)
				.maxAge(0)
				.build();
	}
}
