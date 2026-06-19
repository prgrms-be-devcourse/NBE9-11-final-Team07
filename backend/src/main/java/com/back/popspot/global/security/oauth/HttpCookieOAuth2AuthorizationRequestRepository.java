package com.back.popspot.global.security.oauth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * OAuth2 인가 요청(state 등)을 HTTP 세션이 아닌 쿠키에 저장한다.
 * JWT 기반 stateless 구성에서 세션 없이 OAuth2 로그인을 처리하기 위함이다.
 */
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
		implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

	private static final String COOKIE_NAME = "oauth2_auth_request";
	private static final int COOKIE_EXPIRE_SECONDS = 180;

	@Override
	public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
		return getCookie(request)
				.map(cookie -> deserialize(cookie.getValue()))
				.orElse(null);
	}

	@Override
	public void saveAuthorizationRequest(
			OAuth2AuthorizationRequest authorizationRequest,
			HttpServletRequest request,
			HttpServletResponse response
	) {
		if (authorizationRequest == null) {
			deleteCookie(response);
			return;
		}
		addCookie(response, serialize(authorizationRequest));
	}

	@Override
	public OAuth2AuthorizationRequest removeAuthorizationRequest(
			HttpServletRequest request,
			HttpServletResponse response
	) {
		OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
		if (authorizationRequest != null) {
			deleteCookie(response);
		}
		return authorizationRequest;
	}

	private Optional<Cookie> getCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
				.filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
				.findFirst();
	}

	private void addCookie(HttpServletResponse response, String value) {
		ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
				.path("/")
				.httpOnly(true)
				.maxAge(COOKIE_EXPIRE_SECONDS)
				.sameSite("Lax")
				.build();
		response.addHeader("Set-Cookie", cookie.toString());
	}

	private void deleteCookie(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
				.path("/")
				.httpOnly(true)
				.maxAge(0)
				.sameSite("Lax")
				.build();
		response.addHeader("Set-Cookie", cookie.toString());
	}

	private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(baos)) {
			oos.writeObject(authorizationRequest);
			oos.flush();
			return Base64.getUrlEncoder().encodeToString(baos.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException("OAuth2 인가 요청 직렬화 실패", e);
		}
	}

	private OAuth2AuthorizationRequest deserialize(String value) {
		byte[] bytes = Base64.getUrlDecoder().decode(value);
		try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
			return (OAuth2AuthorizationRequest) ois.readObject();
		} catch (IOException | ClassNotFoundException e) {
			throw new IllegalStateException("OAuth2 인가 요청 역직렬화 실패", e);
		}
	}
}
