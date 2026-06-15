package com.back.popspot.global.security.jwt;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.response.CommonApiResponse;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 인증되지 않은 요청이 보호된 엔드포인트에 접근할 때 401 을 공통 응답 형식으로 반환한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authException
	) throws IOException {
		ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");

		objectMapper.writeValue(
				response.getWriter(),
				CommonApiResponse.error(errorCode.name(), errorCode.getMessage())
		);
	}
}
