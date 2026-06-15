package com.back.popspot.global.security.oauth;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.response.CommonApiResponse;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 로그인 실패 시 공통 응답 형식의 JSON 으로 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

	private final ObjectMapper objectMapper;

	@Override
	public void onAuthenticationFailure(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException exception
	) throws IOException {
		log.warn("OAuth2 로그인 실패: {}", exception.getMessage());

		ErrorCode errorCode = ErrorCode.OAUTH2_LOGIN_FAILED;

		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");

		objectMapper.writeValue(
				response.getWriter(),
				CommonApiResponse.error(errorCode.name(), errorCode.getMessage())
		);
	}
}
