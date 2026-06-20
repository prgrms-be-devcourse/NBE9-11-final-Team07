package com.back.popspot.global.security.oauth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.back.popspot.global.security.jwt.JwtTokenProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * OAuth2 로그인 성공 시 JWT access token 을 HttpOnly 쿠키에 담고 프론트엔드로 리다이렉트한다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

	private static final String ACCESS_TOKEN_COOKIE = "access_token";
	// 프론트엔드가 로그인 상태를 감지하기 위한 읽기 전용(non-HttpOnly) 플래그 쿠키
	private static final String LOGGED_IN_COOKIE = "logged_in";

	private final JwtTokenProvider jwtTokenProvider;

	@Value("${app.frontend-url:http://localhost:3000}")
	private String frontendUrl;

	@Value("${jwt.access-token-validity-seconds}")
	private long accessTokenValiditySeconds;

	@Override
	public void onAuthenticationSuccess(
			HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication
	) throws IOException {
		CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();

		String accessToken = jwtTokenProvider.createAccessToken(
				principal.getUserId(),
				principal.getEmail(),
				principal.getName()
		);

		ResponseCookie accessTokenCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, accessToken)
				.httpOnly(true)
				.secure(false) // 로컬 http 환경. 운영(HTTPS)에서는 true 로 변경한다.
				.path("/")
				.sameSite("Lax")
				.maxAge(accessTokenValiditySeconds)
				.build();

		// JS 에서 읽을 수 있어야 하므로 httpOnly(false). 토큰 자체가 아닌 로그인 여부 플래그.
		ResponseCookie loggedInCookie = ResponseCookie.from(LOGGED_IN_COOKIE, "true")
				.httpOnly(false)
				.secure(false)
				.path("/")
				.sameSite("Lax")
				.maxAge(accessTokenValiditySeconds)
				.build();

		response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.toString());
		response.addHeader(HttpHeaders.SET_COOKIE, loggedInCookie.toString());
		response.sendRedirect(frontendUrl);
	}
}
