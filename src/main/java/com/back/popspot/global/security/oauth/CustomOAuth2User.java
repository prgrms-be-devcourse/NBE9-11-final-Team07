package com.back.popspot.global.security.oauth;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OAuth2 로그인 성공 직후 사용되는 principal. 우리 DB 의 userId 를 함께 들고 있어
 * 성공 핸들러에서 JWT 를 발급할 때 사용한다.
 */
@Getter
@RequiredArgsConstructor
public class CustomOAuth2User implements OAuth2User {

	private final Long userId;
	private final String email;
	private final String name;
	private final Map<String, Object> attributes;

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		// role 없음 — 로그인 여부만 판단하므로 권한은 비워 둔다.
		return List.of();
	}

	@Override
	public String getName() {
		return String.valueOf(userId);
	}
}
