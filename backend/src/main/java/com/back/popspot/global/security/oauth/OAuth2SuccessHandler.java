package com.back.popspot.global.security.oauth;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.back.popspot.domain.auth.dto.TokenResponse;
import com.back.popspot.global.response.CommonApiResponse;
import com.back.popspot.global.security.jwt.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * OAuth2 로그인 성공 시 JWT access token 을 발급해 공통 응답 형식의 JSON 으로 반환한다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

	private final JwtTokenProvider jwtTokenProvider;
	private final ObjectMapper objectMapper;

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

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");

		objectMapper.writeValue(
				response.getWriter(),
				CommonApiResponse.success(new TokenResponse(accessToken))
		);
	}
}
