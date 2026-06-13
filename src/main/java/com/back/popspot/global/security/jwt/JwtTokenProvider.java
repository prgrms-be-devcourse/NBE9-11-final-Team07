package com.back.popspot.global.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT access token 발급/검증. refresh token 은 사용하지 않는다.
 */
@Slf4j
@Component
public class JwtTokenProvider {

	private final SecretKey key;
	private final long accessTokenValidityMillis;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-validity-seconds}") long accessTokenValiditySeconds
	) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenValidityMillis = accessTokenValiditySeconds * 1000;
	}

	public String createAccessToken(Long userId, String email, String name) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + accessTokenValidityMillis);

		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("email", email)
				.claim("name", name)
				.issuedAt(now)
				.expiration(expiry)
				.signWith(key)
				.compact();
	}

	public boolean validate(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			log.debug("유효하지 않은 JWT: {}", e.getMessage());
			return false;
		}
	}

	public Long getUserId(String token) {
		return Long.valueOf(parseClaims(token).getSubject());
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
